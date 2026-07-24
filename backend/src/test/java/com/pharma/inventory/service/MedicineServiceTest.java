package com.pharma.inventory.service;

import com.pharma.inventory.dto.CreateMedicineRequest;
import com.pharma.inventory.dto.CreatePharmaCompanyRequest;
import com.pharma.inventory.dto.MedicineResponse;
import com.pharma.inventory.dto.PharmaCompanyResponse;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.PharmaCompany;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.repository.MedicineRepository;
import com.pharma.inventory.repository.PharmaCompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineService")
class MedicineServiceTest {

    @Mock MedicineRepository medicineRepository;
    @Mock PharmaCompanyRepository pharmaCompanyRepository;
    @InjectMocks MedicineService medicineService;

    private PharmaCompany company;
    private Medicine medicine;

    @BeforeEach
    void setUp() {
        company = PharmaCompany.builder().id(1L).name("Shield FX").description("FIP supplier").active(true).build();
        medicine = Medicine.builder().id(1L).name("Shield FX Vial 10 ml").type(Medicine.MedicineType.VIAL)
                .specification(10.0).concentrationMgPerMl(20.0).price(4000).pharmaCompany(company).active(true).build();
    }

    @Nested @DisplayName("getAll / getAllCompanies")
    class Reads {
        @Test @DisplayName("maps medicines including nested pharma company reference")
        void getAll_mapsFields() {
            when(medicineRepository.findAll()).thenReturn(List.of(medicine));

            List<MedicineResponse> result = medicineService.getAll();

            assertThat(result).hasSize(1);
            MedicineResponse r = result.get(0);
            assertThat(r.getId()).isEqualTo(1L);
            assertThat(r.getName()).isEqualTo("Shield FX Vial 10 ml");
            assertThat(r.getType()).isEqualTo("VIAL");
            assertThat(r.getSpecification()).isEqualTo(10.0);
            assertThat(r.getConcentrationMgPerMl()).isEqualTo(20.0);
            assertThat(r.getPrice()).isEqualTo(4000);
            assertThat(r.getPharmaCompany().getId()).isEqualTo(1L);
            assertThat(r.getPharmaCompany().getName()).isEqualTo("Shield FX");
        }

        @Test @DisplayName("maps pharma companies")
        void getAllCompanies_mapsFields() {
            when(pharmaCompanyRepository.findAll()).thenReturn(List.of(company));

            List<PharmaCompanyResponse> result = medicineService.getAllCompanies();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getName()).isEqualTo("Shield FX");
            assertThat(result.get(0).getDescription()).isEqualTo("FIP supplier");
        }
    }

    @Nested @DisplayName("createCompany")
    class CreateCompany {
        @Test @DisplayName("trims name and description, defaults active to true")
        void createCompany_trimsAndActivates() {
            when(pharmaCompanyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            CreatePharmaCompanyRequest req = new CreatePharmaCompanyRequest();
            req.setName("  Shield FX  ");
            req.setDescription("  FIP supplier  ");

            PharmaCompanyResponse result = medicineService.createCompany(req);

            ArgumentCaptor<PharmaCompany> captor = ArgumentCaptor.forClass(PharmaCompany.class);
            verify(pharmaCompanyRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Shield FX");
            assertThat(captor.getValue().getDescription()).isEqualTo("FIP supplier");
            assertThat(captor.getValue().isActive()).isTrue();
            assertThat(result.getName()).isEqualTo("Shield FX");
        }

        @Test @DisplayName("null description stays null, not an empty string")
        void createCompany_nullDescription_staysNull() {
            when(pharmaCompanyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            CreatePharmaCompanyRequest req = new CreatePharmaCompanyRequest();
            req.setName("Shield FX");

            medicineService.createCompany(req);

            ArgumentCaptor<PharmaCompany> captor = ArgumentCaptor.forClass(PharmaCompany.class);
            verify(pharmaCompanyRepository).save(captor.capture());
            assertThat(captor.getValue().getDescription()).isNull();
        }
    }

    @Nested @DisplayName("createMedicine")
    class CreateMedicine {
        @Test @DisplayName("looks up the pharma company and saves the new medicine")
        void createMedicine_savesWithResolvedCompany() {
            when(pharmaCompanyRepository.findById(1L)).thenReturn(Optional.of(company));
            when(medicineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateMedicineRequest req = new CreateMedicineRequest();
            req.setPharmaCompanyId(1L);
            req.setName("  New Med  ");
            req.setType(Medicine.MedicineType.TABLET);
            req.setSpecification(25.0);
            req.setPrice(1000);

            MedicineResponse result = medicineService.createMedicine(req);

            ArgumentCaptor<Medicine> captor = ArgumentCaptor.forClass(Medicine.class);
            verify(medicineRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("New Med");
            assertThat(captor.getValue().getPharmaCompany()).isSameAs(company);
            assertThat(result.getType()).isEqualTo("TABLET");
        }

        @Test @DisplayName("throws ResourceNotFoundException (404) when the pharma company doesn't exist")
        void createMedicine_companyNotFound_throwsResourceNotFound() {
            when(pharmaCompanyRepository.findById(99L)).thenReturn(Optional.empty());

            CreateMedicineRequest req = new CreateMedicineRequest();
            req.setPharmaCompanyId(99L);
            req.setName("New Med");
            req.setType(Medicine.MedicineType.TABLET);
            req.setSpecification(25.0);
            req.setPrice(1000);

            assertThatThrownBy(() -> medicineService.createMedicine(req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
            verifyNoInteractions(medicineRepository);
        }
    }
}
