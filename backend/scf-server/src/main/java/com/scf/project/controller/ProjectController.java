package com.scf.project.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.project.dto.ProjectCreateRequest;
import com.scf.project.dto.ProjectUpdateRequest;
import com.scf.project.dto.ProjectView;
import com.scf.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectView>> list(HttpServletRequest request) {
        return ApiResponse.ok(projectService.list(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(projectService.get(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping
    public ApiResponse<ProjectView> create(@Valid @RequestBody ProjectCreateRequest body, HttpServletRequest request) {
        return ApiResponse.ok(projectService.create(body), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectView> update(
            @PathVariable String id,
            @Valid @RequestBody ProjectUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(projectService.update(id, body), request.getHeader("X-Request-Id"));
    }
}
