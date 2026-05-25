package com.eduaccess.ui;

import com.eduaccess.domain.UserAccount;
import com.eduaccess.domain.UserRole;
import com.eduaccess.service.LoginService;
import com.vaadin.flow.router.BeforeEnterEvent;

public class PermissionChecker {

    public static void checkBookingAccess(BeforeEnterEvent event, LoginService loginService) {
        UserAccount user = loginService.getCurrentUser();
        if (user == null) {
            event.rerouteTo("login/booking");
            return;
        }
        if (!loginService.canAccessBooking()) {
            event.rerouteTo("access-denied");
        }
    }

    public static void checkCancellationAccess(BeforeEnterEvent event, LoginService loginService) {
        UserAccount user = loginService.getCurrentUser();
        if (user == null) {
            event.rerouteTo("login/cancellation");
            return;
        }
        if (!loginService.canAccessCancellation()) {
            event.rerouteTo("access-denied");
        }
    }

    public static void checkAdminAccess(BeforeEnterEvent event, LoginService loginService) {
        UserAccount user = loginService.getCurrentUser();
        if (user == null) {
            event.rerouteTo("login/admin/schedule");
            return;
        }
        if (!loginService.canAccessAdmin()) {
            event.rerouteTo("access-denied");
        }
    }

    public static void checkManagerAccess(BeforeEnterEvent event, LoginService loginService) {
        UserAccount user = loginService.getCurrentUser();
        if (user == null) {
            event.rerouteTo("login/manager/cinemas");
            return;
        }
        if (!loginService.canAccessManager()) {
            event.rerouteTo("access-denied");
        }
    }
}
