package com.zaga.licenciamientoapp.service.fingerprint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultHardwareFingerprintServiceTest {

    private final DefaultHardwareFingerprintService service = new DefaultHardwareFingerprintService();

    @Test
    void huellaEsIndependienteDelOrdenDeLosComponentes() {
        String a = service.compute(1, List.of("cpu:ABC", "board:XYZ", "mac:1122"));
        String b = service.compute(1, List.of("mac:1122", "board:XYZ", "cpu:ABC"));
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void huellaCambiaConElHardware() {
        String a = service.compute(1, List.of("cpu:ABC", "board:XYZ"));
        String b = service.compute(1, List.of("cpu:ABC", "board:OTRO"));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void exigeAlMenosDosComponentesUtiles() {
        assertThatThrownBy(() -> service.compute(1, List.of("cpu:ABC")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaVersionNoSoportada() {
        assertThatThrownBy(() -> service.compute(99, List.of("a", "b", "c")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
