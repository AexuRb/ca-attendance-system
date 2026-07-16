package com.ca.attendance.term.api;

import com.ca.attendance.term.application.TermService;
import com.ca.attendance.term.domain.AcademicTerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/terms")
public class TermController {
    private final TermService terms;

    public TermController(TermService terms) {
        this.terms = terms;
    }

    @GetMapping
    public TermService.TermListResponse list() {
        return terms.list();
    }

    @PostMapping
    public AcademicTerm create(@RequestBody TermService.TermRequest request) {
        return terms.create(request);
    }

    @PutMapping("/{id}")
    public AcademicTerm update(@PathVariable long id, @RequestBody TermService.TermRequest request) {
        return terms.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public AcademicTerm activate(@PathVariable long id,
                                 @RequestBody(required = false) TermService.ActivateRequest request) {
        return terms.activate(id, request);
    }

    @PostMapping("/{id}/settling")
    public AcademicTerm beginSettling(@PathVariable long id) {
        return terms.beginSettling(id);
    }

    @GetMapping("/{id}/settlement/preflight")
    public TermService.SettlementPreflight preflight(@PathVariable long id) {
        return terms.preflight(id);
    }

    @PostMapping("/{id}/settlement/preview")
    public TermService.SettlementPreview preview(@PathVariable long id) {
        return terms.preview(id);
    }

    @PostMapping("/{id}/seal")
    public TermService.SealResult seal(@PathVariable long id) {
        return terms.seal(id);
    }

    @PostMapping("/{id}/reopen")
    public AcademicTerm reopen(@PathVariable long id, @RequestBody TermService.ReopenRequest request) {
        return terms.reopen(id, request);
    }

    @GetMapping("/{id}/settlements")
    public List<TermService.SettlementVersion> settlements(@PathVariable long id) {
        return terms.settlements(id);
    }
}
