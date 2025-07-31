# openai-java-fileid-bug-repro

This repository contains a minimal Java Spring Boot example that **reproduces a bug** in the `openai-java` SDK (v2.20.0), where file ID references (e.g., `file-abc123`) are not correctly recognized when building messages manually using `ChatCompletionMessage.builder()`.

## 🔍 Bug Description

When using the `ChatCompletionCreateParams.Builder` and manually building chat messages with custom roles (system, user, assistant), the model fails to recognize uploaded file references—even when they are correctly embedded in the message content.

However, when using the shortcut methods like `addUserMessage(...)`, the file references are properly handled and the model behaves as expected.

## 💥 Behavior Reproduction

This example includes:

- File upload using `client.files().create(...)`
- Use of `fileId` inside a chat message
- Two versions of prompt creation:
  - ✅ Working: `addUserMessage(...)`
  - ❌ Broken: `ChatCompletionMessage.builder().role(...).content(...)`

## 📎 Related GitHub Issue

[openai/openai-java#566](https://github.com/openai/openai-java/issues/566)

## 📂 Structure

- `OpenAIService.java` – handles file upload and chat requests.
- `MainApplication.java` – basic runner to execute the test.
- `application.yml` – configuration for OpenAI API key.

## 🛠️ Requirements

- Java 17+
- Spring Boot 3+
- openai-java: 2.20.0
