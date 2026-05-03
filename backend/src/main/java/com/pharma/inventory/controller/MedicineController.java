package com.pharma.inventory.controller;
import com.pharma.inventory.repository.*;
import lombok.RequiredArgsConstructor;
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
}
