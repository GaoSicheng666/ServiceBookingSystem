package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.entity.User;
import com.eldercare.repository.UserRepository;
import org.springframework.stereotype.Service;

/** 用户自助服务:查看自己信息。 */
@Service
public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public User getSelf(String username) {
        User u = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        u.setPassword(null);     // 不返回密码
        return u;
    }
}
