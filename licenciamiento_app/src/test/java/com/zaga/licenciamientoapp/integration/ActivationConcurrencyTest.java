package com.zaga.licenciamientoapp.integration;

import com.zaga.licenciamientoapp.domain.EstadoActivador;
import com.zaga.licenciamientoapp.repository.ActivadorRepository;
import com.zaga.licenciamientoapp.repository.PcInfoRepository;
import com.zaga.licenciamientoapp.service.ActivationService;
import com.zaga.licenciamientoapp.service.LicenseAdminService;
import com.zaga.licenciamientoapp.web.dto.InstallerHardwareRequest;
import com.zaga.licenciamientoapp.web.dto.LicenciaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de concurrencia: N equipos intentan reclamar el mismo serial SIN_USO de forma
 * simultanea. El bloqueo SELECT ... FOR UPDATE debe garantizar que solo uno lo consiga
 * y que no se dupliquen filas de PC_INFO.
 */
@SpringBootTest
class ActivationConcurrencyTest {

    @Autowired
    private ActivationService activationService;

    @Autowired
    private LicenseAdminService adminService;

    @Autowired
    private ActivadorRepository activadorRepository;

    @Autowired
    private PcInfoRepository pcInfoRepository;

    @Test
    void soloUnEquipoPuedeReclamarUnSerialSinUso() throws Exception {
        LicenciaResponse licencia = adminService.crearLicencia("PRODUCTO_CONC", "ACME", 1);
        long activadorId = licencia.seriales().get(0).activadorId();
        String serial = licencia.seriales().get(0).serial();

        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            final int idx = i;
            resultados.add(pool.submit(() -> {
                InstallerHardwareRequest req = new InstallerHardwareRequest(
                        serial, "PRODUCTO_CONC", "inst-" + idx, null,
                        List.of("cpu:" + idx, "board:" + idx), 1, "PC-" + idx, "Windows");
                salida.await();
                try {
                    activationService.activar(req);
                    return true;
                } catch (Exception e) {
                    return false; // rechazo esperado para los perdedores
                }
            }));
        }

        salida.countDown();
        int exitos = 0;
        for (Future<Boolean> f : resultados) {
            if (f.get()) {
                exitos++;
            }
        }
        pool.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(activadorRepository.findById(activadorId).orElseThrow().getEstado())
                .isEqualTo(EstadoActivador.OCUPADO);
        assertThat(pcInfoRepository.findByActivadorId(activadorId)).isPresent();
    }
}
