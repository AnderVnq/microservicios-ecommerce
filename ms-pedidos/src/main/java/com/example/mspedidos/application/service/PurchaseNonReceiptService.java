package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * Marcar unidades de una compra como no recibidas (spec 8.3). Con la compra y sus unidades bloqueadas se decide: si la
 * seleccion es solo sobrante se aplica en esa misma transaccion (salida de inventario, sin ms_auth ni solicitud); si
 * incluye unidades de pedido se crea una solicitud {@code PURCHASE_NOT_RECEIVED} via
 * {@link StatusChangeRequestUseCase#submit}, que vuelve a bloquear, validar y seleccionar al aplicar.
 * <p>
 * Idempotencia: la {@code Idempotency-Key} se guarda en la marca directa ({@code purchase_non_receipts}) o en la
 * solicitud ({@code status_change_requests}); ambas con indice unico. Con el lock de la compra se busca en las dos:
 * misma clave y mismo hash en la misma compra repite el resultado original; en otra compra o con otro hash es un
 * reuso (422). Si dos peticiones con la misma clave compiten, la constraint unica decide y la perdedora repite.
 */
@Service
class PurchaseNonReceiptService {

    /** Resultado original de la marca: la solicitud (creada o aplicada), o null si fue solo sobrante. */
    record Outcome(StatusChangeRequest request) {
    }

    private final NotReceivedApplier applier;
    private final StatusChangeRequestUseCase requests;
    private final StatusChangeRequestRepositoryPort requestRepository;
    private final PurchaseRepositoryPort purchaseRepository;
    private final ActorProvider actorProvider;
    private final TransactionTemplate tx;

    PurchaseNonReceiptService(NotReceivedApplier applier, StatusChangeRequestUseCase requests,
                              StatusChangeRequestRepositoryPort requestRepository,
                              PurchaseRepositoryPort purchaseRepository, ActorProvider actorProvider,
                              PlatformTransactionManager transactionManager) {
        this.applier = applier;
        this.requests = requests;
        this.requestRepository = requestRepository;
        this.purchaseRepository = purchaseRepository;
        this.actorProvider = actorProvider;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** @return la solicitud creada o aplicada; {@code request} null si la marca fue solo sobrante */
    Outcome markNotReceived(Long purchaseId, String idempotencyKey, String requestHash, int quantity, String reason) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > PurchaseService.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("La cabecera Idempotency-Key es obligatoria (maximo "
                    + PurchaseService.MAX_KEY_LENGTH + " caracteres)");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la peticion es obligatorio");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        Optional<Outcome> replay = replayOf(purchaseId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        String actor = actorProvider.currentActor();
        Outcome direct;
        try {
            direct = tx.execute(status -> {
                NotReceivedApplier.Locked locked = applier.lock(purchaseId);
                // con el lock de la compra (READ COMMITTED) se ve lo que confirmo una peticion con la misma clave
                Optional<Outcome> lockedReplay = replayOf(purchaseId, idempotencyKey, requestHash);
                if (lockedReplay.isPresent()) {
                    return lockedReplay.get();
                }
                Purchase purchase = locked.purchase();
                NotReceivedApplier.ensureSynced(purchase);
                int setAside = requestRepository.sumPendingNotReceived(purchaseId);
                int available = purchase.pending() - setAside;
                if (quantity > available) {
                    throw new PurchaseQuantityExceededException("La compra " + purchaseId + " solo tiene " + available
                            + " unidades pendientes disponibles; se pidieron " + quantity);
                }
                if (quantity > locked.surplusPending()) {
                    // toca unidades de pedido: es un retroceso y va por solicitud (fuera de esta transaccion)
                    return null;
                }
                applier.applyLocked(locked, quantity, setAside, reason, null, "direct by " + actor + ": " + reason,
                        actor, idempotencyKey, requestHash);
                return new Outcome(null);
            });
        } catch (DataIntegrityViolationException e) {
            return replayOf(purchaseId, idempotencyKey, requestHash).orElseThrow(() -> e);
        }
        if (direct != null) {
            // repeticion o marca directa recien confirmada; sincronizar es idempotente
            applier.syncAfterCommit(purchaseId);
            return direct;
        }
        // submit consulta ms_auth sin locks y luego, en su transaccion, vuelve a bloquear, validar y (con el lock de
        // la compra) detectar una peticion con la misma clave que se adelanto
        try {
            return new Outcome(requests.submit(StatusChangeRequest.openNotReceived(purchaseId, quantity, reason, actor,
                    idempotencyKey, requestHash)));
        } catch (NotReceivedApplier.KeyAlreadyUsedException | DataIntegrityViolationException e) {
            return replayOf(purchaseId, idempotencyKey, requestHash).orElseThrow(() -> e);
        }
    }

    /** Las claves son globales: la misma clave en otra compra o con otro cuerpo es un reuso (422). */
    private Optional<Outcome> replayOf(Long purchaseId, String key, String hash) {
        Optional<StatusChangeRequest> request = requestRepository.findByIdempotencyKey(key);
        if (request.isPresent()) {
            StatusChangeRequest r = request.get();
            check(key, r.getType() == StatusChangeRequestType.PURCHASE_NOT_RECEIVED
                    && purchaseId.equals(r.getPurchaseId()), hash.equals(r.getRequestHash()));
            return Optional.of(new Outcome(r));
        }
        return purchaseRepository.findNonReceiptByKey(key).map(n -> {
            check(key, purchaseId.equals(n.getPurchaseId()), hash.equals(n.getRequestHash()));
            return new Outcome(null);
        });
    }

    private static void check(String key, boolean samePurchase, boolean sameHash) {
        if (!samePurchase) {
            throw new IdempotencyKeyReusedException("La Idempotency-Key " + key + " ya se uso para otra compra");
        }
        if (!sameHash) {
            throw new IdempotencyKeyReusedException(
                    "La Idempotency-Key " + key + " ya se uso con otro cuerpo de peticion");
        }
    }
}
