package com.hostelmanagement.expenses;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.expenses.ExpenseService.CategoryTotal;
import com.hostelmanagement.expenses.ExpenseService.ExpenseDto;
import com.hostelmanagement.expenses.ExpenseService.ExpenseRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Expenses")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_EXPENSE_MANAGE')")
public class ExpenseController {

    private final ExpenseService service;
    private final AppClock clock;

    @GetMapping
    public PageResponse<ExpenseDto> list(@RequestParam(required = false) Long hostelId,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(hostelId, category, from, to, page, size);
    }

    @GetMapping("/by-category")
    public List<CategoryTotal> byCategory(@RequestParam(required = false) Long hostelId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to == null ? clock.today() : to;
        LocalDate f = from == null ? t.withDayOfMonth(1) : from;
        return service.byCategory(hostelId, f, t);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDto create(@Valid @RequestBody ExpenseRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest req) {
        return service.update(id, req);
    }
}
