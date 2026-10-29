package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.ClientesPort;
import com.example.mspedidos.domain.exception.ClientesUnavailableException;
import com.example.mspedidos.domain.exception.PersonNotFoundException;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.Person;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Supplier;

@Component
class ClientesRestAdapter implements ClientesPort {

    private final RestClient restClient;

    ClientesRestAdapter(@Qualifier("clientesRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Person getHolder(Long holderId) {
        String detail = "titular " + holderId;
        ClienteResponse body = call(detail, () -> restClient.get()
                .uri("/api/v1/clientes/{id}", holderId)
                .retrieve()
                .body(ClienteResponse.class));
        if (body == null) {
            throw new PersonNotFoundException(detail);
        }
        Person person = new Person(body.id(), fullName(body.nombres(), body.apellidos()),
                new ImporterDocument(body.tipoDocumento(), body.numeroDocumento()), Boolean.TRUE.equals(body.activo()));
        return requireActive(person, detail);
    }

    @Override
    public Person getFamilyMember(Long holderId, Long familyMemberId) {
        String detail = "familiar " + familyMemberId + " del titular " + holderId;
        FamiliarResponse body = call(detail, () -> restClient.get()
                .uri("/api/v1/clientes/{holderId}/familiares/{id}", holderId, familyMemberId)
                .retrieve()
                .body(FamiliarResponse.class));
        if (body == null || !holderId.equals(body.clienteId())) {
            throw new PersonNotFoundException(detail);
        }
        return requireActive(toPerson(body), detail);
    }

    @Override
    public List<Person> listFamilyMembers(Long holderId) {
        String detail = "titular " + holderId;
        List<FamiliarResponse> body = call(detail, () -> restClient.get()
                .uri("/api/v1/clientes/{holderId}/familiares", holderId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<FamiliarResponse>>() { }));
        return body == null ? List.of() : body.stream().map(ClientesRestAdapter::toPerson).toList();
    }

    private <T> T call(String detail, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new PersonNotFoundException(detail);
            }
            throw new ClientesUnavailableException("ms-clientes respondio " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new ClientesUnavailableException("ms-clientes no disponible", e);
        }
    }

    private static Person requireActive(Person person, String detail) {
        if (!person.active()) {
            throw new PersonNotFoundException(detail + " (inactivo)");
        }
        return person;
    }

    private static Person toPerson(FamiliarResponse f) {
        return new Person(f.id(), fullName(f.nombres(), f.apellidos()),
                new ImporterDocument(f.tipoDocumento(), f.numeroDocumento()), Boolean.TRUE.equals(f.activo()));
    }

    private static String fullName(String nombres, String apellidos) {
        return nombres + " " + apellidos;
    }

    record ClienteResponse(Long id, String nombres, String apellidos, String tipoDocumento,
                           String numeroDocumento, Boolean activo) {
    }

    record FamiliarResponse(Long id, Long clienteId, String nombres, String apellidos, String tipoDocumento,
                            String numeroDocumento, Boolean activo) {
    }
}
