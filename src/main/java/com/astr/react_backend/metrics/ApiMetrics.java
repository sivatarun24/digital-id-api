package com.astr.react_backend.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ApiMetrics {

    private final MeterRegistry registry;

    // ── Auth counters ────────────────────────────────────────────

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter registerSuccess;
    private final Counter registerFailure;
    private final Counter passwordChangeSuccess;
    private final Counter passwordChangeFailure;
    private final Counter passwordResetRequest;
    private final Counter passwordResetSuccess;
    private final Counter passwordResetFailure;
    private final Counter tokenRefresh;
    private final Counter logoutCount;
    private final Counter profileUpdate;

    // ── Email counters ───────────────────────────────────────────

    private final Counter emailSentAuth;
    private final Counter emailSentMarketing;
    private final Counter emailSentPersonal;
    private final Counter emailFailed;

    // ── Timers ───────────────────────────────────────────────────

    private final Timer loginTimer;
    private final Timer registerTimer;
    private final Timer emailSendTimer;

    public ApiMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.loginSuccess = Counter.builder("auth.login")
                .tag("status", "success")
                .description("Successful logins")
                .register(registry);

        this.loginFailure = Counter.builder("auth.login")
                .tag("status", "failure")
                .description("Failed logins")
                .register(registry);

        this.registerSuccess = Counter.builder("auth.register")
                .tag("status", "success")
                .description("Successful registrations")
                .register(registry);

        this.registerFailure = Counter.builder("auth.register")
                .tag("status", "failure")
                .description("Failed registrations")
                .register(registry);

        this.passwordChangeSuccess = Counter.builder("auth.password.change")
                .tag("status", "success")
                .description("Successful password changes")
                .register(registry);

        this.passwordChangeFailure = Counter.builder("auth.password.change")
                .tag("status", "failure")
                .description("Failed password changes")
                .register(registry);

        this.passwordResetRequest = Counter.builder("auth.password.reset.request")
                .description("Password reset requests")
                .register(registry);

        this.passwordResetSuccess = Counter.builder("auth.password.reset")
                .tag("status", "success")
                .description("Successful password resets")
                .register(registry);

        this.passwordResetFailure = Counter.builder("auth.password.reset")
                .tag("status", "failure")
                .description("Failed password resets")
                .register(registry);

        this.tokenRefresh = Counter.builder("auth.token.refresh")
                .description("Token refresh count")
                .register(registry);

        this.logoutCount = Counter.builder("auth.logout")
                .description("Logout count")
                .register(registry);

        this.profileUpdate = Counter.builder("auth.profile.update")
                .description("Profile updates")
                .register(registry);

        this.emailSentAuth = Counter.builder("email.sent")
                .tag("type", "auth")
                .description("Auth emails sent")
                .register(registry);

        this.emailSentMarketing = Counter.builder("email.sent")
                .tag("type", "marketing")
                .description("Marketing emails sent")
                .register(registry);

        this.emailSentPersonal = Counter.builder("email.sent")
                .tag("type", "personal")
                .description("Personal emails sent")
                .register(registry);

        this.emailFailed = Counter.builder("email.failed")
                .description("Failed email sends")
                .register(registry);

        this.loginTimer = Timer.builder("auth.login.duration")
                .description("Login processing time")
                .register(registry);

        this.registerTimer = Timer.builder("auth.register.duration")
                .description("Registration processing time")
                .register(registry);

        this.emailSendTimer = Timer.builder("email.send.duration")
                .description("Email send processing time")
                .register(registry);
    }

    // ── Auth metric methods ──────────────────────────────────────

    public void recordLoginSuccess() { loginSuccess.increment(); }
    public void recordLoginFailure() { loginFailure.increment(); }
    public void recordRegisterSuccess() { registerSuccess.increment(); }
    public void recordRegisterFailure() { registerFailure.increment(); }
    public void recordPasswordChangeSuccess() { passwordChangeSuccess.increment(); }
    public void recordPasswordChangeFailure() { passwordChangeFailure.increment(); }
    public void recordPasswordResetRequest() { passwordResetRequest.increment(); }
    public void recordPasswordResetSuccess() { passwordResetSuccess.increment(); }
    public void recordPasswordResetFailure() { passwordResetFailure.increment(); }
    public void recordTokenRefresh() { tokenRefresh.increment(); }
    public void recordLogout() { logoutCount.increment(); }
    public void recordProfileUpdate() { profileUpdate.increment(); }

    // ── Email metric methods ─────────────────────────────────────

    public void recordEmailSent(String type) {
        switch (type.toLowerCase()) {
            case "auth" -> emailSentAuth.increment();
            case "marketing" -> emailSentMarketing.increment();
            case "personal" -> emailSentPersonal.increment();
        }
    }

    public void recordEmailFailed() { emailFailed.increment(); }

    // ── Timer helpers ────────────────────────────────────────────

    public <T> T timeLogin(Supplier<T> action) {
        return loginTimer.record(action);
    }

    public <T> T timeRegister(Supplier<T> action) {
        return registerTimer.record(action);
    }

    public void timeEmailSend(Runnable action) {
        emailSendTimer.record(action);
    }

    public void recordEmailDuration(long millis) {
        emailSendTimer.record(millis, TimeUnit.MILLISECONDS);
    }
}
