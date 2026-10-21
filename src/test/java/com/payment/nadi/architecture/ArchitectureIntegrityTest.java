package com.payment.nadi.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architectural Integrity & Compliance Test Suite.
 * Automates Clean / Hexagonal Architecture fitness functions for the Nadi Payment Service.
 */
class ArchitectureIntegrityTest {

    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java", "com", "payment", "nadi");

    @Test
    @DisplayName("Architecture Rule 1: Domain layer must remain 100% pure (Zero framework / outer-layer dependencies)")
    void domainLayerMustBeIndependentOfOuterLayersAndFrameworks() throws IOException {
        Path domainPath = SOURCE_ROOT.resolve("domain");
        assertTrue(Files.exists(domainPath), "Domain package directory must exist");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(domainPath)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ")) {
                            if (line.contains("com.payment.nadi.application")
                                    || line.contains("com.payment.nadi.infrastructure")
                                    || line.contains("com.payment.nadi.interfaces")
                                    || line.contains("org.springframework")
                                    || line.contains("jakarta.persistence")
                                    || line.contains("com.fasterxml.jackson")
                                    || line.contains("org.apache.kafka")) {
                                violations.add(String.format("%s:%d -> %s", file.getFileName(), i + 1, line));
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Domain layer purity violated! Outer layer or framework imports detected in domain:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("Architecture Rule 2: Inbound Ports (Use Cases) must not leak Infrastructure Entities")
    void inboundPortsMustNotLeakInfrastructureEntities() throws IOException {
        Path inPortsPath = SOURCE_ROOT.resolve("application").resolve("port").resolve("in");
        assertTrue(Files.exists(inPortsPath), "Application port.in package must exist");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(inPortsPath)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ") && line.contains("com.payment.nadi.infrastructure")) {
                            violations.add(String.format("%s:%d -> %s", file.getFileName(), i + 1, line));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Inbound ports purity violated! Infrastructure entities leaked in use case interfaces:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("Architecture Rule 3: REST Controllers must not directly inject Spring Data repositories")
    void restControllersMustNotDirectlyInjectSpringDataRepositories() throws IOException {
        Path restPath = SOURCE_ROOT.resolve("interfaces").resolve("rest");
        assertTrue(Files.exists(restPath), "Interfaces REST package must exist");

        List<String> repositoryInjections = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(restPath)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ") && line.contains("infrastructure.persistence.repository")) {
                            repositoryInjections.add(String.format("%s:%d -> %s", file.getFileName(), i + 1, line));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(repositoryInjections.isEmpty(),
                "REST Controllers must not directly inject Spring Data repositories (must use application use cases):\n"
                        + String.join("\n", repositoryInjections));
    }

    @Test
    @DisplayName("Architecture Rule 4: REST Controllers must not directly inject outbound ports")
    void restControllersMustNotInjectOutboundPorts() throws IOException {
        Path restPath = SOURCE_ROOT.resolve("interfaces").resolve("rest");
        assertTrue(Files.exists(restPath), "Interfaces REST package must exist");

        List<String> outboundInjections = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(restPath)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ") && line.contains("application.port.out")) {
                            outboundInjections.add(String.format("%s:%d -> %s", file.getFileName(), i + 1, line));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(outboundInjections.isEmpty(),
                "REST Controllers must only depend on inbound use case ports (application.port.in):\n"
                        + String.join("\n", outboundInjections));
    }

    @Test
    @DisplayName("Architecture Rule 5: Application DTOs must not import persistence entities")
    void applicationDtosMustNotImportPersistenceEntities() throws IOException {
        Path dtoPath = SOURCE_ROOT.resolve("application").resolve("dto");
        assertTrue(Files.exists(dtoPath), "Application DTO package must exist");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(dtoPath)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.startsWith("import ") && line.contains("infrastructure.persistence.entity")) {
                            violations.add(String.format("%s:%d -> %s", file.getFileName(), i + 1, line));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Application DTOs must not import infrastructure entities:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("Architecture Rule 6: Optimistic Locking (@Version) must be declared on high-concurrency entities")
    void concurrencySensitiveEntitiesMustHaveOptimisticLocking() throws IOException {
        Path entityPath = SOURCE_ROOT.resolve("infrastructure").resolve("persistence").resolve("entity");
        assertTrue(Files.exists(entityPath), "Entity directory must exist");

        List<String> checkedEntities = List.of("PaymentEntity.java", "UserAccountEntity.java");
        for (String entityFileName : checkedEntities) {
            Path file = entityPath.resolve(entityFileName);
            assertTrue(Files.exists(file), entityFileName + " must exist");
            List<String> lines = Files.readAllLines(file);
            boolean hasVersion = lines.stream().anyMatch(l -> l.trim().equals("@Version"));
            assertTrue(hasVersion, entityFileName + " must declare @Version for lost-update prevention");
        }
    }

    @Test
    @DisplayName("Architecture Rule 7: Kafka Event Producer must publish only via After-Commit Transactional Event Listeners")
    void eventsMustBePublishedAfterCommit() throws IOException {
        Path listenerFile = SOURCE_ROOT.resolve("infrastructure").resolve("messaging").resolve("KafkaPaymentEventListener.java");
        assertTrue(Files.exists(listenerFile), "KafkaPaymentEventListener must exist");

        List<String> lines = Files.readAllLines(listenerFile);
        boolean hasAfterCommit = lines.stream().anyMatch(l -> l.contains("TransactionPhase.AFTER_COMMIT"));
        assertTrue(hasAfterCommit, "KafkaPaymentEventListener must specify TransactionPhase.AFTER_COMMIT to ensure transactional consistency");
    }

    @Test
    @DisplayName("Architecture Rule 8: Wallet Service must implement Deadlock-Free Ordered Account Locking")
    void walletServiceMustImplementDeadlockFreeOrderedLocking() throws IOException {
        Path walletFile = SOURCE_ROOT.resolve("infrastructure").resolve("wallet").resolve("DefaultWalletService.java");
        assertTrue(Files.exists(walletFile), "DefaultWalletService must exist");

        List<String> lines = Files.readAllLines(walletFile);
        boolean hasLockOrdering = lines.stream().anyMatch(l -> l.contains("compareTo(accountB)"));
        assertTrue(hasLockOrdering, "DefaultWalletService must enforce lexicographical lock ordering to prevent P2P deadlocks");
    }
}
