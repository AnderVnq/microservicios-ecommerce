package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.domain.exception.ClientesUnavailableException;
import com.example.mspedidos.domain.exception.PersonNotFoundException;
import com.example.mspedidos.domain.model.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientesRestAdapterTest {

    private static final String BASE = "http://clientes";

    private MockRestServiceServer server;
    private ClientesRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .requestInterceptor(new AuthorizationForwardingInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ClientesRestAdapter(builder.build());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static final String HOLDER_JSON = """
            {"id":7,"nombres":"Ana","apellidos":"Diaz","tipoDocumento":"DNI","numeroDocumento":"12345678",
             "email":"a@b.c","telefono":"1","activo":%s}""";

    @Test
    void getHolder_mapsDocumentAndName() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(HOLDER_JSON.formatted("true"), MediaType.APPLICATION_JSON));

        Person person = adapter.getHolder(7L);

        assertThat(person.id()).isEqualTo(7L);
        assertThat(person.fullName()).isEqualTo("Ana Diaz");
        assertThat(person.document().type()).isEqualTo("DNI");
        assertThat(person.document().number()).isEqualTo("12345678");
        assertThat(person.active()).isTrue();
        server.verify();
    }

    @Test
    void getHolder_404_throwsPersonNotFound() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.getHolder(7L)).isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void getHolder_inactive_throwsPersonNotFound() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7"))
                .andRespond(withSuccess(HOLDER_JSON.formatted("false"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.getHolder(7L))
                .isInstanceOf(PersonNotFoundException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void getHolder_500_throwsClientesUnavailable() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.getHolder(7L)).isInstanceOf(ClientesUnavailableException.class);
    }

    @Test
    void getHolder_401_throwsClientesUnavailable() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.getHolder(7L)).isInstanceOf(ClientesUnavailableException.class);
    }

    @Test
    void getFamilyMember_ofOtherHolder_throwsPersonNotFound() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7/familiares/9"))
                .andRespond(withSuccess("""
                        {"id":9,"clienteId":8,"nombres":"Leo","apellidos":"Diaz","tipoDocumento":"DNI",
                         "numeroDocumento":"999","parentesco":"HIJO","activo":true}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.getFamilyMember(7L, 9L)).isInstanceOf(PersonNotFoundException.class);
    }

    @Test
    void getFamilyMember_mapsPerson() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7/familiares/9"))
                .andRespond(withSuccess("""
                        {"id":9,"clienteId":7,"nombres":"Leo","apellidos":"Diaz","tipoDocumento":"CE",
                         "numeroDocumento":"999","parentesco":"HIJO","activo":true}""", MediaType.APPLICATION_JSON));

        Person person = adapter.getFamilyMember(7L, 9L);

        assertThat(person.fullName()).isEqualTo("Leo Diaz");
        assertThat(person.document().type()).isEqualTo("CE");
    }

    @Test
    void listFamilyMembers_returnsAllIncludingInactive() {
        server.expect(requestTo(BASE + "/api/v1/clientes/7/familiares"))
                .andRespond(withSuccess("""
                        [{"id":9,"clienteId":7,"nombres":"Leo","apellidos":"Diaz","tipoDocumento":"DNI",
                          "numeroDocumento":"999","parentesco":"HIJO","activo":true},
                         {"id":10,"clienteId":7,"nombres":"Mia","apellidos":"Diaz","tipoDocumento":"DNI",
                          "numeroDocumento":"888","parentesco":"HIJA","activo":false}]""",
                        MediaType.APPLICATION_JSON));

        List<Person> persons = adapter.listFamilyMembers(7L);

        assertThat(persons).extracting(Person::active).containsExactly(true, false);
    }

    @Test
    void forwardsAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        server.expect(requestTo(BASE + "/api/v1/clientes/7"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess(HOLDER_JSON.formatted("true"), MediaType.APPLICATION_JSON));

        adapter.getHolder(7L);

        server.verify();
    }
}
