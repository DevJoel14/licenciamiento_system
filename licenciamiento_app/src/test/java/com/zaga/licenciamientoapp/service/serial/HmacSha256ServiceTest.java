package com.zaga.licenciamientoapp.service.serial;

import com.zaga.licenciamientoapp.config.LicensingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256ServiceTest {

    private HmacSha256Service service() {
        LicensingProperties props = new LicensingProperties();
        props.setHmacSecret("secreto-de-prueba");
        return new HmacSha256Service(props);
    }

    @Test
    void normalizaEliminandoSeparadoresYMayuscular() {
        HmacSha256Service service = service();
        assertThat(service.normalize("abcde-fghij klmno")).isEqualTo("ABCDEFGHIJKLMNO");
    }

    @Test
    void hashEsDeterministaEIgnoraFormatoDelSerial() {
        HmacSha256Service service = service();
        String a = service.serialToHash("ABCDE-FGHIJ-KLMNO");
        String b = service.serialToHash("abcde fghij klmno");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void serialesDistintosProducenHashDistinto() {
        HmacSha256Service service = service();
        assertThat(service.serialToHash("AAAAA-BBBBB"))
                .isNotEqualTo(service.serialToHash("AAAAA-CCCCC"));
    }

    @Test
    void hashDependeDelSecreto() {
        LicensingProperties p1 = new LicensingProperties();
        p1.setHmacSecret("uno");
        LicensingProperties p2 = new LicensingProperties();
        p2.setHmacSecret("dos");
        assertThat(new HmacSha256Service(p1).serialToHash("SERIAL"))
                .isNotEqualTo(new HmacSha256Service(p2).serialToHash("SERIAL"));
    }

    @Test
    void enmascaraSerialParaLogs() {
        HmacSha256Service service = service();
        assertThat(service.mask("ABCDE-FGHIJ-KLMNO")).isEqualTo("****LMNO");
        assertThat(service.mask("AB")).isEqualTo("****");
        assertThat(service.mask(null)).isEqualTo("****");
    }
}
