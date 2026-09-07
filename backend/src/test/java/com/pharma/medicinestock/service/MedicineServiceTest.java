package com.pharma.medicinestock.service;

import com.pharma.medicinestock.dto.CreateMedicineRequest;
import com.pharma.medicinestock.dto.CreatePharmaCompanyRequest;
import com.pharma.medicinestock.dto.MedicineResponse;
import com.pharma.medicinestock.dto.PharmaCompanyResponse;
import com.pharma.medicinestock.entity.Medicine;
import com.pharma.medicinestock.entity.PharmaCompany;
import com.pharma.medicinestock.exception.ResourceNotFoundException;
import com.pharma.medicinestock.repository.MedicineRepository;
import com.pharma.medicinestock.repository.PharmaCompanyRepository;
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

    @Nested @DisplayName("updateMedicine")
    class UpdateMedicine {

        private CreateMedicineRequest validUpdateRequest() {
            CreateMedicineRequest req = new CreateMedicineRequest();
            req.setPharmaCompanyId(1L);
            req.setName("  Updated Name  ");
            req.setType(Medicine.MedicineType.TABLET);
            req.setSpecification(50.0);
            req.setPrice(9000);
            return req;
        }

        @Test @DisplayName("updates every field on the existing medicine and saves")
        void updateMedicine_updatesAllFieldsAndSaves() {
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(pharmaCompanyRepository.findById(1L)).thenReturn(Optional.of(company));
            when(medicineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MedicineResponse result = medicineService.updateMedicine(1L, validUpdateRequest());

            ArgumentCaptor<Medicine> captor = ArgumentCaptor.forClass(Medicine.class);
            verify(medicineRepository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(medicine); // updates in place, not a new entity
            assertThat(captor.getValue().getName()).isEqualTo("Updated Name");
            assertThat(captor.getValue().getType()).isEqualTo(Medicine.MedicineType.TABLET);
            assertThat(captor.getValue().getSpecification()).isEqualTo(50.0);
            assertThat(captor.getValue().getPrice()).isEqualTo(9000);
            assertThat(result.getName()).isEqualTo("Updated Name");
            assertThat(result.getType()).isEqualTo("TABLET");
        }

        @Test @DisplayName("clears concentrationMgPerMl when the request omits it (e.g. switching away from VIAL)")
        void updateMedicine_omittedConcentration_clearsIt() {
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(pharmaCompanyRepository.findById(1L)).thenReturn(Optional.of(company));
            when(medicineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            medicineService.updateMedicine(1L, validUpdateRequest());

            assertThat(medicine.getConcentrationMgPerMl()).isNull();
        }

        @Test @DisplayName("reassigns to a different pharma company when pharmaCompanyId changes")
        void updateMedicine_reassignsPharmaCompany() {
            PharmaCompany otherCompany = PharmaCompany.builder().id(2L).name("MediCure").active(true).build();
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(pharmaCompanyRepository.findById(2L)).thenReturn(Optional.of(otherCompany));
            when(medicineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateMedicineRequest req = validUpdateRequest();
            req.setPharmaCompanyId(2L);

            MedicineResponse result = medicineService.updateMedicine(1L, req);

            assertThat(medicine.getPharmaCompany()).isSameAs(otherCompany);
            assertThat(result.getPharmaCompany().getName()).isEqualTo("MediCure");
        }

        @Test @DisplayName("throws ResourceNotFoundException (404) when the medicine doesn't exist")
        void updateMedicine_medicineNotFound_throwsResourceNotFound() {
            when(medicineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> medicineService.updateMedicine(99L, validUpdateRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
            verifyNoInteractions(pharmaCompanyRepository);
            verify(medicineRepository, never()).save(any());
        }

        @Test @DisplayName("throws ResourceNotFoundException (404) when the new pharma company doesn't exist")
        void updateMedicine_companyNotFound_throwsResourceNotFound() {
            when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
            when(pharmaCompanyRepository.findById(99L)).thenReturn(Optional.empty());

            CreateMedicineRequest req = validUpdateRequest();
            req.setPharmaCompanyId(99L);

            assertThatThrownBy(() -> medicineService.updateMedicine(1L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
            verify(medicineRepository, never()).save(any());
        }
    }
}
