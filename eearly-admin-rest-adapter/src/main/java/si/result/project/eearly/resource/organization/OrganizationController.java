package si.result.project.eearly.resource.organization;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.result.project.eearly.dto.organization.OrganizationDTO;
import si.result.project.eearly.dto.organization.OrganizationLiteDTO;
import si.result.project.eearly.dto.organization.OrganizationUpsertDTO;
import si.result.project.eearly.facade.organization.OrganizationServiceFacade;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.PathFilter;
import si.result.spring.boot.bricks.dto.ResponseDTO;

import java.util.UUID;

@Tag(name = "Organization Controller")
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationServiceFacade organizationServiceFacade;

    @GetMapping(value = "", produces = "application/json")
    public ResponseEntity<ResponseDTO<PagedModel<OrganizationLiteDTO>>> getPage(
            @ParameterObject @PageableDefault final Pageable pageable,
            @PathFilter Filter filter) {
        return new ResponseDTO<>(organizationServiceFacade.getPage(filter, pageable)).ok();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ResponseDTO<OrganizationDTO>> getById(
            @PathVariable(name = "id") final UUID id) {
        return new ResponseDTO<>(organizationServiceFacade.getById(id)).ok();
    }

    @PostMapping(value = "", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ResponseDTO<OrganizationUpsertDTO>> createOrganization(
            @RequestBody final OrganizationUpsertDTO organizationUpsertDTO) {
        return new ResponseDTO<>(organizationServiceFacade.create(organizationUpsertDTO)).created();
    }
}
