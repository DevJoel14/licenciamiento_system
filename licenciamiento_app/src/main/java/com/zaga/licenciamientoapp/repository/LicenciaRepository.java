package com.zaga.licenciamientoapp.repository;

import com.zaga.licenciamientoapp.domain.Licencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LicenciaRepository extends JpaRepository<Licencia, Long> {

    List<Licencia> findByCodigoProducto(String codigoProducto);

    List<Licencia> findByCodigoProductoAndClienteRef(String codigoProducto, String clienteRef);

    /**
     * Busqueda administrativa flexible: ambos filtros son opcionales, la comparacion
     * ignora mayusculas/minusculas y admite coincidencias parciales. En el producto se
     * tratan {@code _} y espacio como equivalentes (CRUSTY_FUSION == CRUSTY FUSION).
     */
    @Query("""
            select l from Licencia l
            where (:producto is null
                   or lower(replace(l.codigoProducto, '_', ' ')) like lower(concat('%', :producto, '%')))
              and (:cliente is null
                   or lower(l.clienteRef) like lower(concat('%', :cliente, '%')))
            order by l.id
            """)
    List<Licencia> buscar(@Param("producto") String producto, @Param("cliente") String cliente);
}
