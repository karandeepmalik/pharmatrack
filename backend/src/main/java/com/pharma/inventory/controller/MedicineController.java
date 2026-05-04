package com.pharma.inventory.controller;
import com.pharma.inventory.entity.Medicine;
import com.pharma.inventory.entity.PharmaCompany;
import com.pharma.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
@RestController @RequestMapping("/api/medicines") @RequiredArgsConstructor
public class MedicineController {
    private final MedicineRepository medicineRepo;
    private final PharmaCompanyRepository pharmaRepo;

    @GetMapping @Transactional(readOnly=true)
    public List<Map<String,Object>> getAll() {
        return medicineRepo.findAll().stream().map(m -> {
            Map<String,Object> map=new LinkedHashMap<>();
            map.put("id",m.getId());map.put("name",m.getName());map.put("type",m.getType());map.put("specification",m.getSpecification());map.put("concentrationMgPerMl",m.getConcentrationMgPerMl());map.put("price",m.getPrice());
            if(m.getPharmaCompany()!=null){Map<String,Object> co=new LinkedHashMap<>();co.put("id",m.getPharmaCompany().getId());co.put("name",m.getPharmaCompany().getName());map.put("pharmaCompany",co);}
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/companies") @Transactional(readOnly=true)
    public List<Map<String,Object>> getCompanies() {
        return pharmaRepo.findAll().stream().map(c -> {
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",c.getId());m.put("name",c.getName());m.put("description",c.getDescription());return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/companies")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String,Object>> createCompany(@RequestBody Map<String,String> req) {
        String name = req.get("name");
        String description = req.get("description");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        }
        PharmaCompany c = new PharmaCompany();
        c.setName(name.trim());
        c.setDescription(description != null ? description.trim() : null);
        c.setActive(true);
        PharmaCompany saved = pharmaRepo.save(c);
        Map<String,Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("name", saved.getName());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String,Object>> createMedicine(@RequestBody Map<String,Object> req) {
        Object pharmaIdObj = req.get("pharmaCompanyId");
        if (pharmaIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "pharmaCompanyId is required"));
        }
        Long pharmaId = Long.valueOf(pharmaIdObj.toString());
        PharmaCompany company = pharmaRepo.findById(pharmaId)
            .orElse(null);
        if (company == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Pharma company not found"));
        }
        Object nameObj = req.get("name");
        Object typeObj = req.get("type");
        Object specObj = req.get("specification");
        Object priceObj = req.get("price");
        if (nameObj == null || typeObj == null || specObj == null || priceObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "name, type, specification and price are required"));
        }
        Medicine m = new Medicine();
        m.setName(nameObj.toString().trim());
        m.setType(Medicine.MedicineType.valueOf(typeObj.toString()));
        m.setSpecification(Double.valueOf(specObj.toString()));
        Object conc = req.get("concentrationMgPerMl");
        if (conc != null && !conc.toString().isBlank()) {
            m.setConcentrationMgPerMl(Double.valueOf(conc.toString()));
        }
        m.setPrice(Integer.valueOf(priceObj.toString()));
        m.setPharmaCompany(company);
        m.setActive(true);
        Medicine saved = medicineRepo.save(m);
        Map<String,Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("name", saved.getName());
        return ResponseEntity.ok(resp);
    }
}
