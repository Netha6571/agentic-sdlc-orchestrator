package com.org.orchestrator.governance;

import com.org.orchestrator.model.StageId;

import java.util.Scanner;

/**
 * Human sign-off point for high-impact stages.
 * Behind an interface so the engine does not care how the human is asked.
 */
public interface ApprovalGate {

    /**
     * Ask for approval. Returns true if approved, false if rejected.
     * A rejected stage is marked failed and that branch stops —
     * a rejected high-impact change never slips through.
     */
    boolean requestApproval(StageId stageId, String description);

    // --- Two built-in implementations ---

    /**
     * Approves everything automatically — for hands-off demos and tests.
     */
    static ApprovalGate autoApprove() {
        return (stageId, description) -> {
            System.out.println("[auto-approve] " + stageId + ": " + description);
            return true;
        };
    }

    /**
     * Prints the pending change and reads yes/no from the console.
     * Any answer starting with 'y' (case-insensitive) is approval.
     */
    static ApprovalGate console() {
        return (stageId, description) -> {
            System.out.println();
            System.out.println("=== APPROVAL REQUIRED: " + stageId + " ===");
            System.out.println(description);
            System.out.print("Approve? (yes/no): ");
            System.out.flush();

            Scanner scanner = new Scanner(System.in);
            String answer = scanner.nextLine().strip().toLowerCase();
            boolean approved = answer.startsWith("y");

            System.out.println(approved ? "[approved]" : "[rejected]");
            return approved;
        };
    }
}
