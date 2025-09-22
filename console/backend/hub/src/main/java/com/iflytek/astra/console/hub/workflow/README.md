# Workflow Conversation Feature Documentation

Workflow conversation functionality implemented based on iFlytek AgentClient and SseEmitterUtil.

## Feature Overview

This workflow conversation feature provides complete workflow-driven intelligent dialogue capabilities, supporting:

- **Streaming Workflow Conversations**: Real-time conversations based on specified workflow IDs
- **Workflow Interruption Handling**: Support for user interaction and selection during workflow execution
- **Workflow Recovery**: Support for workflow recovery operations after interruption
- **Connection Disconnection Protection**: Ensures data can be saved normally even if frontend disconnects
- **Event-Driven Architecture**: Support for various event handling in workflows

## Core Components

### 1. DTOs (Data Transfer Objects)

#### WorkflowChatRequest
Workflow conversation request parameters:
```java
{
  "flowId": "workflow_123",           // Workflow ID (required)
  "userId": "user_456",               // User ID (required)
  "chatId": "chat_789",               // Chat session ID (required)
  "messages": [...],                  // Conversation history (required)
  "stream": true,                     // Whether to enable streaming response
  "parameters": {...},                // Workflow custom parameters
  "ext": {...},                       // Extended data
  "fileIds": ["file1", "file2"]       // File ID list
}
```

#### WorkflowResumeRequest
Workflow resume request parameters:
```java
{
  "eventId": "event_123",             // Event ID (required)
  "eventType": "interrupt",           // Event type (required)
  "operation": "resume",              // Operation type: resume/ignore/abort (required)
  "content": "User reply content",     // Resume content (required when operation=resume)
  "userId": "user_456",               // User ID
  "chatId": "chat_789"                // Chat ID
}
```

#### WorkflowEventData
Workflow event data structure:
```java
{
  "eventId": "event_123",             // Event ID
  "eventType": "interrupt",           // Event type
  "needReply": true,                  // Whether reply is needed
  "value": {
    "type": "direct|option",          // Answer type
    "message": "Prompt message",       // Main message
    "content": "Question content",     // Q&A node content
    "option": [...]                   // Option content (when type=option)
  }
}
```

### 2. Service Layer

#### WorkflowChatService
Core service class providing:

- `workflowChatStream()`: Start workflow conversation stream
- `resumeWorkflow()`: Resume interrupted workflow
- Complete SSE stream processing and error handling
- Workflow event processing and data persistence

#### Key Features:

1. **Connection Protection**: Continue background data processing after client disconnection
2. **Event Handling**: Support for workflow interruption, user selection and other events
3. **Data Persistence**: Automatically save conversation records, reasoning process, and source tracing data
4. **Error Recovery**: Comprehensive exception handling and recovery mechanisms

### 3. Controller Layer

#### WorkflowChatController
RESTful API endpoints:

- `POST /api/v1/workflow/chat/stream` - Start workflow conversation stream
- `POST /api/v1/workflow/chat/resume` - Resume workflow conversation
- `POST /api/v1/workflow/chat/stop/{streamId}` - Stop workflow conversation stream
- `GET /api/v1/workflow/chat/status` - Query workflow conversation status
- `GET /api/v1/workflow/health` - Health check

### 4. Configuration

#### WorkflowConfig
Workflow configuration items:
```yaml
workflow:
  enabled: true                       # Whether to enable workflow functionality
  timeout-ms: 300000                  # Workflow timeout (5 minutes)
  max-concurrent-workflows: 100       # Maximum concurrent workflow count
  event-cache-expire-seconds: 1800    # Event cache expiration time (30 minutes)
  debug-enabled: false                # Whether to enable debug logging
  file-upload:
    enabled: true                     # Whether to enable file upload
    max-file-size: 10485760          # Maximum file size (10MB)
    allowed-types: txt,pdf,doc...     # Supported file types
    storage-path: /tmp/workflow/uploads # File storage path
```

## API Usage Examples

### 1. Start Workflow Conversation

```bash
curl -X POST "http://localhost:8080/api/v1/workflow/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "flowId": "workflow_example",
    "userId": "user123",
    "chatId": "chat456",
    "messages": [
      {
        "role": "user",
        "content": "I want to start a workflow conversation"
      }
    ],
    "stream": true,
    "parameters": {
      "temperature": 0.7
    }
  }'
```

### 2. Resume Workflow Conversation

```bash
curl -X POST "http://localhost:8080/api/v1/workflow/chat/resume" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "eventId": "event_123",
    "eventType": "interrupt",
    "operation": "resume",
    "content": "Continue workflow execution",
    "userId": "user123",
    "chatId": "chat456"
  }'
```

### 3. Stop Workflow Conversation

```bash
curl -X POST "http://localhost:8080/api/v1/workflow/chat/stop/chat456_user123_1234567890"
```

## SSE Event Format

### Regular Conversation Data
```json
{
  "type": "data",
  "content": "Reply content generated by workflow",
  "timestamp": 1234567890
}
```

### Workflow Interrupt Event
```json
{
  "type": "workflow_interrupt",
  "eventData": {
    "eventId": "event_123",
    "eventType": "interrupt",
    "needReply": true,
    "value": {
      "type": "option",
      "content": "Please select the next action:",
      "option": [
        {"id": "1", "text": "Continue", "selected": false},
        {"id": "2", "text": "Stop", "selected": false}
      ]
    }
  }
}
```

### Workflow Complete Event
```json
{
  "type": "workflow_complete",
  "finalResult": "Workflow execution result",
  "timestamp": 1234567890
}
```

## Workflow Operation Types

### Interrupt Resume Operations
- `resume`: Resume workflow execution, requires content parameter
- `ignore`: Ignore current issue and continue with subsequent processes
- `abort`: Terminate the entire workflow conversation

### Event Types
- `interrupt`: Workflow interrupt event, requires user interaction
- `complete`: Workflow completion event
- `error`: Workflow execution error event

## Integration Guide

### Integration with Existing Systems

1. **Data Persistence**: Reuse existing `ChatDataService` for data saving
2. **SSE Management**: Use existing `SseEmitterUtil` for connection management
3. **Configuration Management**: Follow existing configuration patterns and environment variable conventions
4. **Error Handling**: Unified exception handling and logging

### Extension Points

1. **Custom Workflow Processors**: Can extend processing logic for specific workflows
2. **Event Listeners**: Can add workflow event listening and processing
3. **Data Converters**: Can customize workflow data conversion logic
4. **Caching Strategies**: Can customize workflow state caching strategies

## Important Notes

1. **Workflow ID Management**: Ensure workflow ID validity and permission control
2. **Concurrency Control**: Pay attention to workflow concurrency limits
3. **Timeout Handling**: Set reasonable workflow timeout periods
4. **Error Recovery**: Implement comprehensive error recovery mechanisms
5. **Data Security**: Ensure workflow data security and privacy protection

## Monitoring and Debugging

1. **Logging**: Complete operation logs and error logs
2. **Performance Monitoring**: Workflow execution time and resource usage monitoring
3. **State Tracking**: Tracking of workflow state changes
4. **Debug Mode**: Configurable detailed debug information output