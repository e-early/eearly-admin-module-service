package si.result.project.eearly.grpc.caretaker;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import io.grpc.ManagedChannel;
import si.result.eearly.genproto.CaretakerServiceGrpc;
import si.result.eearly.genproto.ErrorCode;
import si.result.eearly.genproto.UpsertCaretakerRequest;
import si.result.eearly.genproto.UpsertCaretakerResponse;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.port.caretaker.CaretakerClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcCaretakerClient implements CaretakerClient {

    private final GrpcChannelFactory channelFactory;

    @Override
    public void upsertCaretaker(Caretaker caretaker) {
        log.info("Upserting caretaker: {}", caretaker.getId());

        ManagedChannel channel = channelFactory.createChannel("mobile-backend");
        CaretakerServiceGrpc.CaretakerServiceFutureStub stub = CaretakerServiceGrpc.newFutureStub(channel);

        si.result.eearly.genproto.Caretaker protoCaretaker = si.result.eearly.genproto.Caretaker.newBuilder()
                .setId(caretaker.getId().toString())
                .setName(caretaker.getFirstName() + " " + caretaker.getLastName())
                .setFacilityId("")
                .build();

        UpsertCaretakerRequest request = UpsertCaretakerRequest.newBuilder()
                .setCaretaker(protoCaretaker)
                .build();

        ListenableFuture<UpsertCaretakerResponse> futureResponse = stub.upsertCaretaker(request);

        Futures.addCallback(futureResponse, new FutureCallback<>() {
            @Override
            public void onSuccess(UpsertCaretakerResponse response) {
                try {
                    if (response.hasError() && response.getError().getCode() != ErrorCode.OK) {
                        handleErrorResponse(caretaker.getId(), response.getError());
                    } else {
                        log.info("Successfully upserted caretaker: {}", caretaker.getId());
                    }
                } finally {
                    shutdownChannel(channel);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Error calling CaretakerService.UpsertCaretaker for caretaker {}: {}",
                        caretaker.getId(), t.getMessage(), t);
                shutdownChannel(channel);
            }
        }, MoreExecutors.directExecutor());
    }

    private void handleErrorResponse(java.util.UUID caretakerId, si.result.eearly.genproto.Error error) {
        ErrorCode errorCode = error.getCode();
        String errorMessage = error.getMessage();

        switch (errorCode) {
            case VALIDATION_ERROR -> log.warn("Validation error upserting caretaker {}: {}",
                    caretakerId, errorMessage);
            case ALREADY_EXISTS -> log.info("Caretaker already exists {}: {}",
                    caretakerId, errorMessage);
            case INTERNAL_ERROR -> log.error("Internal error upserting caretaker {}: {}",
                    caretakerId, errorMessage);
            default -> log.error("Unknown error upserting caretaker {}: {} - {}",
                    caretakerId, errorCode, errorMessage);
        }
    }

    private void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Channel shutdown interrupted", e);
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
