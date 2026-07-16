package com.ca.attendance.term.application;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.term.domain.AcademicTerm;
import com.ca.attendance.term.domain.TermStatus;
import com.ca.attendance.term.infrastructure.AcademicTermRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TermWritePolicy {
    private final AcademicTermRepository terms;

    public TermWritePolicy(AcademicTermRepository terms) {
        this.terms = terms;
    }

    public AcademicTerm requirePublicAttendanceTerm(LocalDate date) {
        return terms.writableForDate(date, false)
                .orElseThrow(() -> ApiException.conflict("当前没有可签到的活动学期，请联系管理员"));
    }

    public AcademicTerm requireBusinessWriteTerm(LocalDate date, Role role) {
        boolean allowSettling = role == Role.PRESIDENT || role == Role.ADMIN;
        AcademicTerm term = terms.writableForDate(date, allowSettling)
                .orElseThrow(() -> ApiException.conflict("所选日期不属于可编辑学期"));
        requireWritable(term, role);
        return term;
    }

    public AcademicTerm requireScheduleWriteTerm(Long termId, Role role) {
        AcademicTerm term = termId == null
                ? terms.active().orElseThrow(() -> ApiException.conflict("请先激活一个学期"))
                : terms.find(termId).orElseThrow(() -> ApiException.notFound("学期不存在"));
        requireWritable(term, role);
        return term;
    }

    public void requireWritable(AcademicTerm term, Role role) {
        if (term.status() == TermStatus.SEALED || term.status() == TermStatus.DRAFT) {
            throw ApiException.conflict("该学期当前不可修改");
        }
        if (term.status() == TermStatus.SETTLING && role != Role.PRESIDENT && role != Role.ADMIN) {
            throw ApiException.forbidden("学期结算期间仅会长和管理员可以修改数据");
        }
    }
}
