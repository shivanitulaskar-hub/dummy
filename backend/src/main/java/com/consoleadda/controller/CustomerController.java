package com.consoleadda.controller;

import com.consoleadda.dto.PageResponse;
import com.consoleadda.dto.customer.CustomerHistory;
import com.consoleadda.dto.customer.CustomerRequest;
import com.consoleadda.dto.customer.CustomerResponse;
import com.consoleadda.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customer master. Operators can look up, add and correct customers; totals are maintained by billing. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Search by name, mobile or bill number. */
    @GetMapping
    public PageResponse<CustomerResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "25") int size) {
        return customerService.search(q, page, size);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @GetMapping("/{id}/history")
    public CustomerHistory history(@PathVariable Long id) {
        return customerService.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }
}
