package pl.stojak.miniiam.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.stojak.miniiam.auth.dto.response.RoleResponse;
import pl.stojak.miniiam.auth.service.RoleService;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Application roles available in the realm (spec §6.2); SERVICE and built-in Keycloak roles are omitted. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<RoleResponse> list() {
        return roleService.listRoles();
    }
}
