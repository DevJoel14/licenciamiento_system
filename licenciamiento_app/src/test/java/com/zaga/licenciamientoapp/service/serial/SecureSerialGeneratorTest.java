package com.zaga.licenciamientoapp.service.serial;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureSerialGeneratorTest {

    private SecureSerialGenerator generator() {
        LicensingProperties props = new LicensingProperties();
        props.getSerial().setGroups(5);
        props.getSerial().setGroupLength(5);
        return new SecureSerialGenerator(props);
    }

    @Test
    void generaFormatoConGruposSeparadosPorGuion() {
        String serial = generator().generar();
        String[] partes = serial.split("-");
        assertThat(partes).hasSize(5);
        for (String p : partes) {
            assertThat(p).hasSize(5);
            assertThat(p).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]+");
        }
    }

    @Test
    void generaSerialesUnicos() {
        SecureSerialGenerator gen = generator();
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            assertThat(vistos.add(gen.generar())).isTrue();
        }
    }
}
