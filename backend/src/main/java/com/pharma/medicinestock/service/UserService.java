package com.pharma.medicinestock.service;
import com.pharma.medicinestock.dto.*;
import com.pharma.medicinestock.entity.User;
import com.pharma.medicinestock.exception.ResourceNotFoundException;
import com.pharma.medicinestock.repository.MedicineStockAdjustmentRepository;
import com.pharma.medicinestock.repository.MedicineStockRepository;
import com.pharma.medicinestock.repository.TransactionRepository;
import com.pharma.medicinestock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final MedicineStockRepository medicineStockRepository;
    private final TransactionRepository transactionRepository;
    private final MedicineStockAdjustmentRepository adjustmentRepository;
    private final PasswordEncoder passwordEncoder;
    @Transactional
    public UserResponse register(RegisterRequest req){
        if(userRepository.existsByUsername(req.getUsername())) throw new IllegalArgumentException("Username already taken");
        if(userRepository.existsByEmail(req.getEmail())) throw new IllegalArgumentException("Email already registered");
        User saved = userRepository.save(User.builder()
            .username(req.getUsername()).email(req.getEmail()).fullName(req.getFullName())
            .password(passwordEncoder.encode(req.getPassword()))
            .role("ADMIN".equalsIgnoreCase(req.getRole())?User.Role.ADMIN:User.Role.USER)
            .active(true).build());
        return toResponse(saved);
    }
    @Transactional(readOnly=true)
    public UserResponse getByUsername(String username){ return toResponse(findEntityByUsername(username)); }
    @Transactional(readOnly=true)
    public List<UserResponse> getAll(){
        return userRepository.findAll().stream().map(UserService::toResponse).toList();
    }
    @Transactional
    public UserResponse toggleActive(Long id){
        User u=userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("User",id));
        u.setActive(!u.isActive());
        return toResponse(userRepository.save(u));
    }
    @Transactional
    public void changePassword(String username,String oldPw,String newPw){
        User u=findEntityByUsername(username);
        if(!passwordEncoder.matches(oldPw,u.getPassword())) throw new IllegalArgumentException("Current password is incorrect");
        u.setPassword(passwordEncoder.encode(newPw)); userRepository.save(u);
    }
    @Transactional
    public void deleteUser(Long id){
        User u=userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("User",id));
        transactionRepository.nullifyApprovedBy(id);
        transactionRepository.deleteBySubmittedById(id);
        adjustmentRepository.nullifyAdjustedBy(id);
        adjustmentRepository.deleteByUserId(id);
        medicineStockRepository.deleteByUserId(id);
        userRepository.delete(u);
    }
    @Transactional
    public void adminChangePassword(Long userId, String newPassword){
        User u=userRepository.findById(userId).orElseThrow(()->new ResourceNotFoundException("User",userId));
        if(newPassword==null||newPassword.length()<8) throw new IllegalArgumentException("Password must be at least 8 characters");
        u.setPassword(passwordEncoder.encode(newPassword)); userRepository.save(u);
    }

    private User findEntityByUsername(String username){
        return userRepository.findByUsername(username).orElseThrow(()->new ResourceNotFoundException("User",username));
    }

    private static UserResponse toResponse(User u){
        UserResponse r = new UserResponse();
        r.setId(u.getId()); r.setUsername(u.getUsername()); r.setFullName(u.getFullName());
        r.setEmail(u.getEmail()); r.setRole(u.getRole().name()); r.setActive(u.isActive());
        return r;
    }
}
