package com.zaga.licenciamientoapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaga.licenciamientoapp.domain.EstadoActivador;
import com.zaga.licenciamientoapp.repository.ActivadorRepository;
import com.zaga.licenciamientoapp.repository.PcInfoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion del flujo completo: alta de licencia -> validacion del
 * instalador -> activacion -> verificacion -> traslado -> revocacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LicensingFlowIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";
    private static final String PRODUCTO = "SISTEMA_TALLER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActivadorRepository activadorRepository;

    @Autowired
    private PcInfoRepository pcInfoRepository;

    private JsonNode crearLicencia(int seriales) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/licencias")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoProducto\":\"" + PRODUCTO + "\",\"clienteRef\":\"ACME\",\"cantidadSeriales\":" + seriales + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult activar(String serial, String instalacionId, String cpu, String board) throws Exception {
        String body = "{\"serial\":\"" + serial + "\","
                + "\"codigoProducto\":\"" + PRODUCTO + "\","
                + "\"instalacionId\":\"" + instalacionId + "\","
                + "\"versionHuella\":1,"
                + "\"nombreEquipo\":\"PC-" + instalacionId + "\","
                + "\"sistemaOperativo\":\"Windows\","
                + "\"componentesHardware\":[\"cpu:" + cpu + "\",\"board:" + board + "\"]}";
        return mockMvc.perform(post("/api/activacion/activar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult verificar(String serial, String instalacionId, String cpu, String board) throws Exception {
        String body = "{\"serial\":\"" + serial + "\","
                + "\"codigoProducto\":\"" + PRODUCTO + "\","
                + "\"instalacionId\":\"" + instalacionId + "\","
                + "\"versionHuella\":1,"
                + "\"componentesHardware\":[\"cpu:" + cpu + "\",\"board:" + board + "\"]}";
        return mockMvc.perform(post("/api/activacion/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult validarHardware(String serial, String producto, String cpu, String board) throws Exception {
        String body = "{\"serial\":\"" + serial + "\","
                + "\"codigoProducto\":\"" + producto + "\","
                + "\"instalacionId\":\"PC-INSTALADOR\","
                + "\"versionHuella\":1,"
                + "\"componentesHardware\":[\"cpu:" + cpu + "\",\"board:" + board + "\"]}";
        return mockMvc.perform(post("/api/instalador/validar-hardware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void validacionConHardwareDelInstalador() throws Exception {
        JsonNode licencia = crearLicencia(1);
        long activadorId = licencia.get("seriales").get(0).get("activadorId").asLong();
        String serial = licencia.get("seriales").get(0).get("serial").asText();

        // 0) Antes de instalar: SIN_USO y sin pc_info
        assertThat(activadorRepository.findById(activadorId).orElseThrow().getEstado())
                .isEqualTo(EstadoActivador.SIN_USO);
        assertThat(pcInfoRepository.findByActivadorId(activadorId)).isEmpty();

        // 1) SIN_USO -> permite en cualquier equipo y registra la PC (bind)
        JsonNode inicial = objectMapper.readTree(
                validarHardware(serial, PRODUCTO, "cpuA", "boardA").getResponse().getContentAsString());
        assertThat(inicial.get("permitido").asBoolean()).isTrue();
        assertThat(inicial.get("codigo").asText()).isEqualTo("601");

        // El serial quedo OCUPADO y se creo la fila de pc_info
        assertThat(activadorRepository.findById(activadorId).orElseThrow().getEstado())
                .isEqualTo(EstadoActivador.OCUPADO);
        assertThat(pcInfoRepository.findByActivadorId(activadorId)).isPresent();
        assertThat(pcInfoRepository.findByActivadorId(activadorId).orElseThrow().getInstalacionId())
                .isEqualTo("PC-INSTALADOR");

        // 2) Misma PC (misma huella) -> permite reinstall
        JsonNode misma = objectMapper.readTree(
                validarHardware(serial, PRODUCTO, "cpuA", "boardA").getResponse().getContentAsString());
        assertThat(misma.get("permitido").asBoolean()).isTrue();
        assertThat(misma.get("codigo").asText()).isEqualTo("601");

        // 3) Otra PC (huella distinta) -> rechaza
        JsonNode otra = objectMapper.readTree(
                validarHardware(serial, PRODUCTO, "cpuB", "boardB").getResponse().getContentAsString());
        assertThat(otra.get("permitido").asBoolean()).isFalse();
        assertThat(otra.get("codigo").asText()).isEqualTo("602");
        assertThat(otra.get("motivo").asText()).isEqualTo("EQUIPO_NO_AUTORIZADO");

        // 4) Producto incorrecto -> rechaza
        JsonNode prod = objectMapper.readTree(
                validarHardware(serial, "OTRO_PRODUCTO", "cpuA", "boardA").getResponse().getContentAsString());
        assertThat(prod.get("motivo").asText()).isEqualTo("PRODUCTO_NO_COINCIDE");

        // 5) Serial inexistente -> rechaza
        JsonNode noExiste = objectMapper.readTree(
                validarHardware("AAAA-BBBB-CCCC-DDDD", PRODUCTO, "cpuA", "boardA").getResponse().getContentAsString());
        assertThat(noExiste.get("motivo").asText()).isEqualTo("SERIAL_NO_EXISTE");

        // 6) Sin hardware -> datos invalidos
        JsonNode sinHw = objectMapper.readTree(
                mockMvc.perform(post("/api/instalador/validar-hardware")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"serial\":\"" + serial + "\",\"codigoProducto\":\"" + PRODUCTO + "\"}"))
                        .andReturn().getResponse().getContentAsString());
        assertThat(sinHw.get("motivo").asText()).isEqualTo("DATOS_INVALIDOS");
    }

    @Test
    void cuerpoJsonMalFormadoDevuelve400() throws Exception {
        // Cuerpo ilegible (falta la llave de cierre) -> debe ser 400 BAD_REQUEST, no 500.
        mockMvc.perform(post("/api/instalador/validar-hardware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serial\":\"ABCDE-FGHIJ-KLMNP-QRSTU-VWXYZ2\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tipo").value("VALIDACION"));
    }

    @Test
    void flujoCompletoDeLicenciamiento() throws Exception {
        JsonNode licencia = crearLicencia(1);
        long licenciaId = licencia.get("id").asLong();
        long activadorId = licencia.get("seriales").get(0).get("activadorId").asLong();
        String serial = licencia.get("seriales").get(0).get("serial").asText();
        assertThat(serial).isNotBlank();

        // 1) Validacion de instalador (idempotente) -> permite
        mockMvc.perform(post("/api/instalador/validar").param("serial", serial).param("producto", PRODUCTO))
                .andExpect(status().isOk())
                .andExpect(content().string("601"));
        mockMvc.perform(get("/api/instalador/validar").param("serial", serial).param("producto", PRODUCTO))
                .andExpect(content().string("601"));

        // 2) Producto incorrecto -> rechaza
        mockMvc.perform(post("/api/instalador/validar").param("serial", serial).param("producto", "OTRO_PRODUCTO"))
                .andExpect(content().string(containsString("602")));

        // 3) Activacion en el equipo A
        MvcResult activacion = activar(serial, "inst-A", "cpuA", "boardA");
        assertThat(activacion.getResponse().getStatus()).isEqualTo(200);
        JsonNode actJson = objectMapper.readTree(activacion.getResponse().getContentAsString());
        assertThat(actJson.get("exitoso").asBoolean()).isTrue();
        assertThat(actJson.get("estadoActivador").asText()).isEqualTo("OCUPADO");

        // 4) Verificacion desde el mismo equipo
        JsonNode verJson = objectMapper.readTree(
                verificar(serial, "inst-A", "cpuA", "boardA").getResponse().getContentAsString());
        assertThat(verJson.get("exitoso").asBoolean()).isTrue();

        // 5) Activacion desde OTRO equipo -> rechazo
        MvcResult otraPc = activar(serial, "inst-B", "cpuB", "boardB");
        assertThat(otraPc.getResponse().getStatus()).isEqualTo(409);
        JsonNode otraJson = objectMapper.readTree(otraPc.getResponse().getContentAsString());
        assertThat(otraJson.get("codigo").asText()).isEqualTo("EQUIPO_NO_AUTORIZADO");

        // 6) Traslado administrativo -> TRASPASO
        mockMvc.perform(post("/api/admin/activadores/" + activadorId + "/traspaso")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autorizadoPor\":\"soporte@acme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TRASPASO"));

        // 7) Activacion desde el equipo nuevo completa el traslado
        JsonNode traslado = objectMapper.readTree(
                activar(serial, "inst-B", "cpuB", "boardB").getResponse().getContentAsString());
        assertThat(traslado.get("exitoso").asBoolean()).isTrue();
        assertThat(traslado.get("estadoActivador").asText()).isEqualTo("OCUPADO");

        // 8) El equipo original ya no puede usar el serial
        JsonNode original = objectMapper.readTree(
                verificar(serial, "inst-A", "cpuA", "boardA").getResponse().getContentAsString());
        assertThat(original.get("exitoso").asBoolean()).isFalse();

        // 9) Revocacion -> el instalador rechaza
        mockMvc.perform(post("/api/admin/activadores/" + activadorId + "/revocar")
                        .header("X-Admin-Api-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOCADO"));
        mockMvc.perform(post("/api/instalador/validar").param("serial", serial).param("producto", PRODUCTO))
                .andExpect(content().string(containsString("602")));

        // 10) Estado de la licencia
        mockMvc.perform(get("/api/licencia/estado").param("codigoProducto", PRODUCTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeriales").value(1));

        // 11) La licencia desactivada bloquea nuevas activaciones
        mockMvc.perform(post("/api/admin/licencias/" + licenciaId + "/desactivar")
                        .header("X-Admin-Api-Key", ADMIN_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/instalador/validar").param("serial", serial).param("producto", PRODUCTO))
                .andExpect(content().string(containsString("602")));
    }

    @Test
    void busquedaAdministrativaDeLicenciasPorProductoClienteEId() throws Exception {
        // Producto propio para no alterar las aserciones de otros tests sobre SISTEMA_TALLER.
        MvcResult creada = mockMvc.perform(post("/api/admin/licencias")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoProducto\":\"PRODUCTO_BUSQUEDA\",\"clienteRef\":\"ACME BUSQUEDA\",\"cantidadSeriales\":2}"))
                .andExpect(status().isOk())
                .andReturn();
        long licenciaId = objectMapper.readTree(creada.getResponse().getContentAsString()).get("id").asLong();

        // Por producto con otro formato: minusculas y guion bajo en vez de espacio.
        MvcResult porProducto = mockMvc.perform(get("/api/admin/licencias")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .param("codigoProducto", "producto_busqueda"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(contieneLicencia(porProducto, licenciaId)).isTrue();

        // Solo por cliente (sin producto).
        MvcResult porCliente = mockMvc.perform(get("/api/admin/licencias")
                        .header("X-Admin-Api-Key", ADMIN_KEY)
                        .param("clienteRef", "acme busqueda"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(contieneLicencia(porCliente, licenciaId)).isTrue();

        // Sin filtros: devuelve todas y el resumen trae el total de seriales.
        MvcResult todas = mockMvc.perform(get("/api/admin/licencias")
                        .header("X-Admin-Api-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resumen = buscarNodo(todas, licenciaId);
        assertThat(resumen).isNotNull();
        assertThat(resumen.get("totalSeriales").asLong()).isEqualTo(2);

        // Detalle por ID con sus seriales.
        mockMvc.perform(get("/api/admin/licencias/" + licenciaId)
                        .header("X-Admin-Api-Key", ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(licenciaId))
                .andExpect(jsonPath("$.seriales.length()").value(2));
    }

    private JsonNode buscarNodo(MvcResult result, long licenciaId) throws Exception {
        JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode nodo : lista) {
            if (nodo.get("id").asLong() == licenciaId) {
                return nodo;
            }
        }
        return null;
    }

    private boolean contieneLicencia(MvcResult result, long licenciaId) throws Exception {
        return buscarNodo(result, licenciaId) != null;
    }

    @Test
    void operacionesAdministrativasRequierenApiKey() throws Exception {
        mockMvc.perform(get("/api/admin/activadores"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/activadores")
                        .header("X-Admin-Api-Key", "clave-incorrecta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serialInexistenteRechazaInstaladorYActivacion() throws Exception {
        mockMvc.perform(post("/api/instalador/validar").param("serial", "AAAA-BBBB-CCCC-DDDD").param("producto", PRODUCTO))
                .andExpect(content().string(containsString("602")));

        MvcResult activacion = activar("AAAA-BBBB-CCCC-DDDD", "inst-Z", "cpuZ", "boardZ");
        assertThat(activacion.getResponse().getStatus()).isEqualTo(409);
        JsonNode json = objectMapper.readTree(activacion.getResponse().getContentAsString());
        assertThat(json.get("codigo").asText()).isEqualTo("SERIAL_NO_EXISTE");
    }
}
