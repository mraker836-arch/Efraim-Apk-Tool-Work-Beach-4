package com.example.security.scanner

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import java.io.File

class WorkflowSecurityAnalyzer {

    fun analyzeWorkflow(
        workflowContent: String,
        workflowPath: String = ".github/workflows/android-release.yml"
    ): Pair<List<String>, List<SecurityFinding>> {
        val findings = mutableListOf<SecurityFinding>()
        val summaryItems = mutableListOf<String>()

        // Check for permissions: write-all
        if (workflowContent.contains("permissions:\\s*write-all".toRegex()) ||
            workflowContent.contains("permissions: write-all")
        ) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.GITHUB_ACTIONS,
                    severity = SecuritySeverity.HIGH,
                    title = "Excessive GitHub Actions Permissions (write-all)",
                    description = "Workflow grants blanket 'write-all' permissions to GITHUB_TOKEN. A compromised step could modify any repository asset or secret.",
                    evidence = "permissions: write-all detected in $workflowPath",
                    recommendation = "Enforce least-privilege permissions (e.g. contents: write, actions: read, packages: write).",
                    module = "WorkflowSecurity",
                    affectedFile = workflowPath,
                    status = FindingStatus.OPEN
                )
            )
            summaryItems.add("Excessive permissions: write-all")
        } else {
            summaryItems.add("Permissions scoped to least privilege")
        }

        // Check for unpinned 3rd party actions
        val unpinnedActions = mutableListOf<String>()
        val usesRegex = "uses:\\s*([a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+)@([a-zA-Z0-9_.-]+)".toRegex()
        usesRegex.findAll(workflowContent).forEach { match ->
            val action = match.groupValues[1]
            val ref = match.groupValues[2]
            if (!ref.matches("[a-f0-9]{40}".toRegex()) && ref == "master" || ref == "main") {
                unpinnedActions.add("$action@$ref")
            }
        }

        if (unpinnedActions.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.SUPPLY_CHAIN,
                    severity = SecuritySeverity.MEDIUM,
                    title = "Unpinned GitHub Actions Branch References",
                    description = "GitHub Actions are referenced via mutable branch tags ('main'/'master') rather than immutable commit SHAs or semantic version tags.",
                    evidence = "Unpinned references: ${unpinnedActions.joinToString(", ")}",
                    recommendation = "Pin actions to specific semantic releases (e.g. @v4) or full 40-character commit SHAs.",
                    module = "WorkflowSecurity",
                    affectedFile = workflowPath,
                    status = FindingStatus.OPEN
                )
            )
            summaryItems.add("Found unpinned action refs: ${unpinnedActions.size}")
        } else {
            summaryItems.add("Actions pinned to versioned tags")
        }

        // Check for shell injection risks in run: steps
        if (workflowContent.contains("\${{ github.event.issue.title }}") ||
            workflowContent.contains("\${{ github.event.comment.body }}") ||
            workflowContent.contains("\${{ github.head_ref }}")
        ) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.GITHUB_ACTIONS,
                    severity = SecuritySeverity.CRITICAL,
                    title = "Potential Script Injection in GitHub Action Step",
                    description = "Workflow interpolates untrusted GitHub context values directly into shell script blocks.",
                    evidence = "Direct interpolation of github.event payload in run step",
                    recommendation = "Pass untrusted GitHub context variables as intermediate environment variables (env: CONTEXT: \${{ ... }}) instead of inline script strings.",
                    module = "WorkflowSecurity",
                    affectedFile = workflowPath,
                    status = FindingStatus.OPEN
                )
            )
            summaryItems.add("Critical: Potential script injection risk detected")
        }

        return Pair(summaryItems, findings)
    }

    fun analyzeWorkflowFile(file: File): Pair<List<String>, List<SecurityFinding>> {
        if (!file.exists()) {
            return Pair(listOf("Workflow file not found"), emptyList())
        }
        return analyzeWorkflow(file.readText(), file.path)
    }
}
