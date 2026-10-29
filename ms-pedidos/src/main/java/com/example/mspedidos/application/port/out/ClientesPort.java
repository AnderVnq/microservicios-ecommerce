package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.Person;

import java.util.List;

public interface ClientesPort {

    Person getHolder(Long holderId);

    Person getFamilyMember(Long holderId, Long familyMemberId);

    List<Person> listFamilyMembers(Long holderId);
}
