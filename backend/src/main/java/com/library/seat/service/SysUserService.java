package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.library.seat.common.BusinessException;
import com.library.seat.common.Constants;
import com.library.seat.dto.LoginDTO;
import com.library.seat.dto.UserDTO;
import com.library.seat.entity.SysUser;
import com.library.seat.mapper.SysUserMapper;
import com.library.seat.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务：登录、注册、账号维护
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /**
     * 登录：根据账号查询角色，管理员进入管理端，学生进入学生端
     */
    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername().trim())
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(401, "账号不存在，请检查后重新输入");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "密码错误，请重新输入");
        }
        if (StringUtils.hasText(dto.getRole()) && !dto.getRole().equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(401, "该账号不是" + ("ADMIN".equals(dto.getRole()) ? "管理员" : "学生") + "账号，请切换登录身份");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole(), user.getRealName());
        Map<String, Object> result = new HashMap<>(8);
        result.put("token", token);
        result.put("userInfo", user);
        result.put("redirect", Constants.ROLE_ADMIN.equals(user.getRole()) ? "/admin/dashboard" : "/student/home");
        result.put("expireHours", jwtUtil.getExpireHours());
        log.info("用户登录成功: {} ({})", user.getUsername(), user.getRole());
        return result;
    }

    public SysUser getByIdRequired(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 管理员分页查询用户
     */
    public Page<SysUser> pageQuery(Page<SysUser> page, String keyword, String role, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) {
            wrapper.eq(SysUser::getRole, role);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getStudentNo, keyword)
                    .or().like(SysUser::getCollege, keyword));
        }
        wrapper.orderByAsc(SysUser::getId);
        return page(page, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserDTO dto) {
        long exists = count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (exists > 0) {
            throw new BusinessException("账号已存在，请更换账号");
        }
        // 手机号唯一（用于短信验证码找回密码）
        if (StringUtils.hasText(dto.getPhone())) {
            long phoneExists = count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, dto.getPhone().trim()));
            if (phoneExists > 0) {
                throw new BusinessException("该手机号已被其他账号绑定");
            }
            if (!dto.getPhone().trim().matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException("手机号格式不正确");
            }
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername().trim());
        user.setRealName(dto.getRealName());
        user.setPassword(passwordEncoder.encode(StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : "123456"));
        user.setRole(StringUtils.hasText(dto.getRole()) ? dto.getRole() : Constants.ROLE_STUDENT);
        user.setStudentNo(StringUtils.hasText(dto.getStudentNo()) ? dto.getStudentNo() : dto.getUsername());
        user.setCollege(dto.getCollege());
        user.setMajor(dto.getMajor());
        user.setPhone(dto.getPhone());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserDTO dto) {
        SysUser user = getByIdRequired(dto.getId());
        long exists = count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .ne(SysUser::getId, dto.getId()));
        if (exists > 0) {
            throw new BusinessException("账号已被其他用户使用");
        }
        user.setUsername(dto.getUsername().trim());
        user.setRealName(dto.getRealName());
        user.setRole(StringUtils.hasText(dto.getRole()) ? dto.getRole() : user.getRole());
        user.setStudentNo(dto.getStudentNo());
        user.setCollege(dto.getCollege());
        user.setMajor(dto.getMajor());
        user.setPhone(dto.getPhone());
        user.setStatus(dto.getStatus() == null ? user.getStatus() : dto.getStatus());
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        SysUser user = getByIdRequired(id);
        user.setPassword(passwordEncoder.encode("123456"));
        updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPwd, String newPwd) {
        if (!StringUtils.hasText(newPwd) || newPwd.length() < 6) {
            throw new BusinessException("新密码长度不能少于 6 位");
        }
        SysUser user = getByIdRequired(userId);
        if (!passwordEncoder.matches(oldPwd == null ? "" : oldPwd, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(newPwd));
        updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeUser(Long id) {
        SysUser user = getByIdRequired(id);
        if (Constants.ROLE_ADMIN.equals(user.getRole())) {
            long adminCount = count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, Constants.ROLE_ADMIN));
            if (adminCount <= 1) {
                throw new BusinessException("系统至少需要保留一个管理员账号");
            }
        }
        removeById(id);
    }

    public long countStudents() {
        return count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, Constants.ROLE_STUDENT));
    }
}