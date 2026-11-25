package com.fitmanager.api.controller;

import java.net.URI;
import java.util.Set;

import com.fitmanager.api.dto.StudentRequest;
import com.fitmanager.api.dto.StudentResponse;
import com.fitmanager.api.dto.StudentStatusRequest;
import com.fitmanager.api.entity.StudentStatus;
import com.fitmanager.api.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints de alunos (API_CONTRACT.md, seção 2). Sem regra de negócio: apenas HTTP ↔ service. */
@RestController
@RequestMapping("/api/students")
@Tag(name = "Alunos", description = "Cadastro, consulta, edição e ativação/desativação de alunos")
public class StudentController {

    /** Campos permitidos em ?sort= (API_CONTRACT.md, seção 2.6). O desempate por id é feito pelo service. */
    static final Set<String> SORTABLE_FIELDS = Set.of("fullName", "email", "birthDate", "createdAt");

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastrar aluno", description = "Novo aluno sempre nasce com status ACTIVE.")
    @ApiResponse(responseCode = "201", description = "Aluno criado; header Location com a URL do recurso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    @ApiResponse(responseCode = "415", description = "Content-Type diferente de application/json")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        StudentResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/students/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar aluno por id")
    @ApiResponse(responseCode = "200", description = "Aluno encontrado")
    @ApiResponse(responseCode = "400", description = "Id não numérico")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado")
    public StudentResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    @Operation(summary = "Listar e pesquisar alunos",
            description = "Paginado. sort aceita fullName, email, birthDate e createdAt (asc|desc); "
                    + "o servidor sempre desempata por id.")
    @ApiResponse(responseCode = "200", description = "Página de alunos no formato { content, page }")
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido (search, status ou sort)")
    public PagedModel<StudentResponse> search(
            @Parameter(description = "Trecho do nome ou e-mail (até 100 caracteres)")
            @RequestParam(required = false) @Size(max = 100) String search,
            @Parameter(description = "Filtra por status; ausente = todos")
            @RequestParam(required = false) StudentStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        validateSort(pageable.getSort());
        return new PagedModel<>(service.search(search, status, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar aluno", description = "Substitui os dados editáveis. Não altera o status.")
    @ApiResponse(responseCode = "200", description = "Aluno atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado")
    @ApiResponse(responseCode = "409", description = "E-mail pertence a outro aluno")
    @ApiResponse(responseCode = "415", description = "Content-Type diferente de application/json")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ativar ou desativar aluno", description = "Idempotente: repetir o status atual retorna 200.")
    @ApiResponse(responseCode = "200", description = "Status atualizado")
    @ApiResponse(responseCode = "400", description = "Status ausente ou desconhecido")
    @ApiResponse(responseCode = "404", description = "Aluno não encontrado")
    @ApiResponse(responseCode = "415", description = "Content-Type diferente de application/json")
    public StudentResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StudentStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    /** Campo fora da lista ou opção ignorecase → 400. Direções válidas (asc/desc) já são interpretadas pelo Spring. */
    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty()) || order.isIgnoreCase()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro de ordenação inválido.");
            }
        }
    }
}
