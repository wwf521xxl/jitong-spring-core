package com.jitong.core.seed;

import com.jitong.core.common.TimeFormat;
import com.jitong.core.config.JitongProperties;
import com.jitong.core.security.PinHasher;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生产环境首次启动：演示数据关闭时，只创建一个可登录的管理员，不写任何业务数据。
 * 管理员登录后再创建工序、路线、员工。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductionAdminSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final JitongProperties properties;
    private final PinHasher pinHasher;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.getSeed().isDemo()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from employees", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        String password = properties.getSeed().getAdminPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("首次启动必须设置 JITONG_ADMIN_PASSWORD");
        }
        if ("Admin@123456".equals(password)) {
            log.warn("管理员仍在使用演示密码，请首次登录后立即修改，或通过 JITONG_ADMIN_PASSWORD 覆盖");
        }
        Instant now = TimeFormat.nowSeconds(clock);
        jdbcTemplate.update("""
                insert into employees(id, employee_no, name, role, avatar, is_admin,
                    can_create_job, active, pin_hash, token_version, created_at)
                values ('A001', 0, '厂长', '厂长 · 管理员', '李', true, false, true, ?, 0, ?)
                """, pinHasher.hash(password), now.atOffset(java.time.ZoneOffset.UTC));
        jdbcTemplate.update("""
                insert into settings(setting_key, setting_value)
                select 'duplicate_minutes', '10'
                where not exists (select 1 from settings where setting_key = 'duplicate_minutes')
                """);
        jdbcTemplate.update("""
                insert into settings(setting_key, setting_value)
                select 'tolerance', '0.05'
                where not exists (select 1 from settings where setting_key = 'tolerance')
                """);
        jdbcTemplate.update("""
                insert into audit_log(actor_id, action, entity_type, entity_id, detail, created_at)
                values ('system', 'bootstrap_admin', 'system', 'init', '首次启动创建管理员', ?)
                """, now.atOffset(java.time.ZoneOffset.UTC));
        log.info("首次启动已创建管理员 admin，请登录后立即修改密码");
    }
}
