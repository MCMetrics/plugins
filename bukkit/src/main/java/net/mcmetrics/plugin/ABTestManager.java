package net.mcmetrics.plugin;

import net.mcmetrics.shared.MCMetricsAPI;
import net.mcmetrics.shared.models.ABTest;
import net.mcmetrics.shared.models.ABTest.TriggerType;
import net.mcmetrics.shared.models.ABTest.ABTestVariant;
import net.mcmetrics.shared.models.ABTestExposure;
import net.mcmetrics.shared.models.Session;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ABTestManager {
    private final MCMetricsAPI api;
    private final Logger logger;
    private final MCMetricsSpigotPlugin plugin;
    private List<ABTest> activeTests;

    public ABTestManager(MCMetricsSpigotPlugin plugin, MCMetricsAPI api, Logger logger) {
        this.plugin = plugin;
        this.api = api;
        this.logger = logger;
        this.activeTests = new ArrayList<>();
    }

    public void fetchTests() {
        if (api == null) {
            logger.warning("Cannot fetch A/B tests: MCMetrics API is not initialized");
            return;
        }

        api.getABTests()
                .thenAccept(tests -> {
                    activeTests = tests;
                    logger.info("Successfully fetched " + tests.size() + " A/B tests");
                })
                .exceptionally(e -> {
                    logger.severe("Failed to fetch A/B tests: " + e.getMessage());
                    return null;
                });
    }

    public List<ABTest> getActiveTests() {
        return activeTests;
    }

    public void handleJoinTrigger(Player player, boolean isFirstJoin) {
        TriggerType triggerType = isFirstJoin ? TriggerType.FirstJoin : TriggerType.Join;
        List<ABTest> relevantTests = activeTests.stream()
                .filter(test -> test.trigger == triggerType)
                .collect(Collectors.toList());

        for (ABTest test : relevantTests) {
            ABTestVariant variant = selectVariant(test, player.getUniqueId());
            executeVariant(test, variant, player);
        }
    }

    public void handlePurchaseTrigger(Player player) {
        List<ABTest> relevantTests = activeTests.stream()
                .filter(test -> test.trigger == TriggerType.Purchase)
                .collect(Collectors.toList());

        for (ABTest test : relevantTests) {
            ABTestVariant variant = selectVariant(test, player.getUniqueId());
            executeVariant(test, variant, player);
        }
    }

    public ABTest.ABTestVariant triggerCommandTest(ABTest test, Player player) {
        if (test.trigger != ABTest.TriggerType.Command) {
            return null;
        }

        ABTest.ABTestVariant variant = selectVariant(test, player.getUniqueId());
        executeVariant(test, variant, player);
        return variant;
    }

    private ABTestVariant selectVariant(ABTest test, UUID playerUuid) {
        // Create a deterministic hash from the test ID and player UUID
        String seed = test.id.toString() + playerUuid.toString();
        int hash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            hash = Math.abs(Arrays.hashCode(hashBytes));
        } catch (NoSuchAlgorithmException e) {
            logger.warning("Failed to create hash for A/B test variant selection: " + e.getMessage());
            hash = seed.hashCode(); // Fallback to simple string hash
        }

        // Use the hash to create a seeded random number generator
        Random random = new Random(hash);

        // Calculate total probability (weight) of all variants
        int totalWeight = test.variants.stream()
                .mapToInt(v -> v.weight)
                .sum();

        // Generate a random number between 0 and totalWeight
        int roll = random.nextInt(totalWeight);

        // Select the variant based on the weights
        int currentWeight = 0;
        for (ABTestVariant variant : test.variants) {
            currentWeight += variant.weight;
            if (roll < currentWeight) {
                return variant;
            }
        }

        // Fallback to first variant (shouldn't happen)
        logger.warning("Failed to select A/B test variant, falling back to first variant");
        return test.variants.get(0);
    }

    private void executeVariant(ABTest test, ABTestVariant variant, Player player) {
        // Record the exposure in the session
        Session session = plugin.getSessionManager().getSession(player.getUniqueId());
        if (session != null) {
            ABTestExposure exposure = new ABTestExposure();
            exposure.ab_test_id = test.id.toString();
            exposure.variant_name = variant.name;

            // Initialize list if null
            if (session.ab_test_exposures == null) {
                session.ab_test_exposures = new ArrayList<>();
            }
            session.ab_test_exposures.add(exposure);
        } else {
            if (plugin.getConfigManager().getBoolean("main", "debug")) {
                logger.warning("Failed to record A/B test exposure: session not found for player " + player.getName());
            }
        }

        // Skip execution for control variants
        if (variant.action == ABTestVariant.ActionType.Control) {
            return;
        }

        // Replace placeholders in payload
        String payload = variant.payload
                .replace("${player}", player.getName())
                .replace("${uuid}", player.getUniqueId().toString())
                .replace("${variant}", variant.name)
                .replace("${experimentName}", test.name);

        switch (variant.action) {
            case ConsoleCommand:
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload));
                break;

            case PlayerCommand:
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(payload));
                break;

            case PlayerMessage:
                player.sendMessage(payload);
                break;
        }
    }
}