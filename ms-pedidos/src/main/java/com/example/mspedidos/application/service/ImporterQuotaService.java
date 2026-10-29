package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.ImporterQuotaUseCase;
import com.example.mspedidos.application.port.in.PersonQuota;
import com.example.mspedidos.application.port.in.QuotaSummary;
import com.example.mspedidos.application.port.out.ClientesPort;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImporterQuotaService implements ImporterQuotaUseCase {

    private final ClientesPort clientesPort;
    private final QuotaAllocator quotaAllocator;

    @Override
    public QuotaSummary quotaFor(Long holderId, int year) {
        List<PersonQuota> persons = new ArrayList<>();
        persons.add(quotaOf(ImporterType.HOLDER, clientesPort.getHolder(holderId), year));
        clientesPort.listFamilyMembers(holderId).stream()
                .filter(Person::active)
                .forEach(f -> persons.add(quotaOf(ImporterType.FAMILY_MEMBER, f, year)));
        return new QuotaSummary(year, quotaAllocator.maxUnitsPerYear(), persons);
    }

    private PersonQuota quotaOf(ImporterType type, Person person, int year) {
        long used = quotaAllocator.used(person.document(), year);
        return new PersonQuota(type.name(), person.id(), person.fullName(), person.document().type(),
                person.document().number(), used, Math.max(0, quotaAllocator.maxUnitsPerYear() - used));
    }
}
