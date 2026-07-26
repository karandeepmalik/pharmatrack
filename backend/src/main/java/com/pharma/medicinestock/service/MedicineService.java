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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final PharmaCompanyRepository pharmaCompanyRepository;

    @Transactional(readOnly = true)
    public List<MedicineResponse> getAll() {
        return medicineRepository.findAll().stream().map(MedicineService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PharmaCompanyResponse> getAllCompanies() {
        return pharmaCompanyRepository.findAll().stream().map(MedicineService::toResponse).toList();
    }

    @Transactional
    public PharmaCompanyResponse createCompany(CreatePharmaCompanyRequest req) {
        PharmaCompany company = new PharmaCompany();
        company.setName(req.getName().trim());
        company.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        company.setActive(true);
        return toResponse(pharmaCompanyRepository.save(company));
    }

    @Transactional
    public MedicineResponse createMedicine(CreateMedicineRequest req) {
        PharmaCompany company = pharmaCompanyRepository.findById(req.getPharmaCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("PharmaCompany", req.getPharmaCompanyId()));

        Medicine medicine = new Medicine();
        medicine.setName(req.getName().trim());
        medicine.setType(req.getType());
        medicine.setSpecification(req.getSpecification());
        medicine.setConcentrationMgPerMl(req.getConcentrationMgPerMl());
        medicine.setPrice(req.getPrice());
        medicine.setPharmaCompany(company);
        medicine.setActive(true);
        return toResponse(medicineRepository.save(medicine));
    }

    private static PharmaCompanyResponse toResponse(PharmaCompany c) {
        PharmaCompanyResponse r = new PharmaCompanyResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setDescription(c.getDescription());
        return r;
    }

    private static MedicineResponse toResponse(Medicine m) {
        MedicineResponse r = new MedicineResponse();
        r.setId(m.getId());
        r.setName(m.getName());
        r.setType(m.getType().name());
        r.setSpecification(m.getSpecification());
        r.setConcentrationMgPerMl(m.getConcentrationMgPerMl());
        r.setPrice(m.getPrice());
        if (m.getPharmaCompany() != null) {
            MedicineResponse.PharmaRef ref = new MedicineResponse.PharmaRef();
            ref.setId(m.getPharmaCompany().getId());
            ref.setName(m.getPharmaCompany().getName());
            r.setPharmaCompany(ref);
        }
        return r;
    }
}
