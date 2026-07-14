package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.entity.Voucher;
import com.minegolem.backend.dto.request.VoucherRequest;
import com.minegolem.backend.dto.response.VoucherResponse;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.UserRepository;
import com.minegolem.backend.repository.VoucherRepository;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class VoucherService {
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<VoucherResponse> list(UUID userId) {
        user(userId);
        return voucherRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public VoucherResponse create(UUID userId, VoucherRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.startDate().isAfter(request.endDate()))
            throw new BusinessException("La data iniziale non può superare la data finale");
        if (request.cost() != null && request.cost().signum() < 0)
            throw new BusinessException("Il costo non può essere negativo");
        Voucher voucher = Voucher.builder().user(user(userId)).name(clean(request.name())).code(clean(request.code()))
            .cost(request.cost()).startDate(request.startDate()).endDate(request.endDate()).build();
        return toResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public void delete(UUID id) {
        Voucher voucher = voucherRepository.findByIdAndUserGymId(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Voucher non trovato"));
        voucherRepository.delete(voucher);
    }

    private User user(UUID id) {
        return userRepository.findByIdAndGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Cliente non trovato"));
    }
    private UUID gymId() {
        return ((StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getGymId();
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private VoucherResponse toResponse(Voucher v) {
        return new VoucherResponse(v.getId(), v.getUser().getId(), v.getName(), v.getCode(), v.getCost(), v.getStartDate(), v.getEndDate());
    }
}
