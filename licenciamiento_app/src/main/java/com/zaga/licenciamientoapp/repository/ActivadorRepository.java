package com.zaga.licenciamientoapp.repository;

import com.zaga.licenciamientoapp.domain.Activador;
import com.zaga.licenciamientoapp.domain.EstadoActivador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivadorRepository extends JpaRepository<Activador, Long> {

    Optional<Activador> findBySerialHash(String serialHash);

    /**
     * Bloquea la fila del activador mediante SELECT ... FOR UPDATE para que dos
     * equipos no puedan reclamar el mismo serial de forma simultanea.
     * Debe invocarse dentro de una transaccion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activador a where a.serialHash = :serialHash")
    Optional<Activador> findBySerialHashForUpdate(@Param("serialHash") String serialHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activador a where a.id = :id")
    Optional<Activador> findByIdForUpdate(@Param("id") Long id);

    List<Activador> findByLicenciaId(Long licenciaId);

    long countByLicenciaId(Long licenciaId);

    long countByLicenciaIdAndEstado(Long licenciaId, EstadoActivador estado);

}
