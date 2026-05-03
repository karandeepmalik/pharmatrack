package com.pharma.inventory.service;
import com.pharma.inventory.dto.*;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.exception.ResourceNotFoundException;
import com.pharma.inventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Transactional
    public User register(RegisterRequest req){
        if(userRepository.existsByUsername(req.getUsername())) throw new IllegalArgumentException("Username already taken");
        if(userRepository.existsByEmail(req.getEmail())) throw new IllegalArgumentException("Email already registered");
        return userRepository.save(User.builder()
            .username(req.getUsername()).email(req.getEmail()).fullName(req.getFullName())
            .password(passwordEncoder.encode(req.getPassword()))
            .role("ADMIN".equalsIgnoreCase(req.getRole())?User.Role.ADMIN:User.Role.USER)
            .active(true).build());
    }
    @Transactional(readOnly=true)
    public User getByUsername(String username){ return userRepository.findByUsername(username).orElseThrow(()->new ResourceNotFoundException("User",username)); }
    @Transactional(readOnly=true)
    public List<User> getAll(){ return userRepository.findAll(); }
    @Transactional
    public User toggleActive(Long id){
        User u=userRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("User",id));
        u.setActive(!u.isActive()); return userRepository.save(u);
    }
    @Transactional
    public void changePassword(String username,String oldPw,String newPw){
        User u=getByUsername(username);
        if(!passwordEncoder.matches(oldPw,u.getPassword())) throw new IllegalArgumentException("Current password is incorrect");
        u.setPassword(passwordEncoder.encode(newPw)); userRepository.save(u);
    }
    @Transactional
    public void adminChangePassword(Long userId, String newPassword){
        User u=userRepository.findById(userId).orElseThrow(()->new ResourceNotFoundException("User",userId));
        if(newPassword==null||newPassword.length()<8) throw new IllegalArgumentException("Password must be at least 8 characters");
        u.setPassword(passwordEncoder.encode(newPassword)); userRepository.save(u);
    }
}
