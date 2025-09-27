# 🔌 API Usage Examples

This document provides detailed API usage examples for the Xingchen RPA Server.

## 📋 Table of Contents

- [Basic Usage](#basic-usage)
- [Python Client Examples](#python-client-examples)
- [JavaScript Client Examples](#javascript-client-examples)
- [cURL Examples](#curl-examples)
- [Error Handling](#error-handling)
- [Advanced Usage](#advanced-usage)

## 🚀 Basic Usage

### API Endpoints

- **Base URL**: `http://localhost:19999`
- **API Version**: `v1`
- **Main Endpoint**: `/rpa/v1/exec`

### Authentication

All API requests require a Bearer Token in the request headers:

```
Authorization: Bearer <your-token>
```

## 🐍 Python Client Examples

### Basic Async Client

```python
import asyncio
import httpx
import json

class RPAClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

    async def execute_task(self, project_id: str, params: dict = None,
                          exec_position: str = "EXECUTOR", sid: str = None):
        """Execute RPA task"""
        url = f"{self.base_url}/rpa/v1/exec"

        payload = {
            "project_id": project_id,
            "exec_position": exec_position,
            "params": params or {},
            "sid": sid or ""
        }

        async with httpx.AsyncClient() as client:
            async with client.stream(
                "POST", url,
                headers=self.headers,
                json=payload,
                timeout=600  # 10 minutes timeout
            ) as response:
                if response.status_code != 200:
                    raise Exception(f"Request failed: {response.status_code}")

                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        try:
                            data = json.loads(line[6:])  # Remove "data: " prefix
                            yield data
                        except json.JSONDecodeError:
                            continue

# Usage Example
async def main():
    client = RPAClient("http://localhost:19999", "your-token-here")

    try:
        async for event in client.execute_task(
            project_id="test-project-123",
            params={
                "action": "automate_task",
                "target": "web_scraping",
                "config": {
                    "url": "https://example.com",
                    "timeout": 30
                }
            },
            sid="unique-session-id"
        ):
            print(f"Received event: {event}")

            # Check task status
            if event.get("code") == 0:  # Success
                print("✅ Task executed successfully")
                print(f"Result: {event.get('data')}")
                break
            elif event.get("code") != 0:  # Error
                print(f"❌ Task execution failed: {event.get('message')}")
                break

    except Exception as e:
        print(f"Request failed: {e}")

# Run example
if __name__ == "__main__":
    asyncio.run(main())
```

### Synchronous Client Version

```python
import requests
import json
import time

class SyncRPAClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

    def execute_task(self, project_id: str, params: dict = None,
                    exec_position: str = "EXECUTOR", sid: str = None):
        """Execute RPA task (synchronous version)"""
        url = f"{self.base_url}/rpa/v1/exec"

        payload = {
            "project_id": project_id,
            "exec_position": exec_position,
            "params": params or {},
            "sid": sid or ""
        }

        with requests.post(
            url,
            headers=self.headers,
            json=payload,
            stream=True,
            timeout=600
        ) as response:
            if response.status_code != 200:
                raise Exception(f"Request failed: {response.status_code}")

            for line in response.iter_lines(decode_unicode=True):
                if line and line.startswith("data: "):
                    try:
                        data = json.loads(line[6:])
                        yield data
                    except json.JSONDecodeError:
                        continue

# Usage Example
def main():
    client = SyncRPAClient("http://localhost:19999", "your-token-here")

    for event in client.execute_task(
        project_id="test-project-123",
        params={"action": "test"},
        sid="sync-session-id"
    ):
        print(f"Event: {event}")

        if event.get("code") == 0:
            print("Task completed")
            break
        elif event.get("code") != 0:
            print("Task failed")
            break

if __name__ == "__main__":
    main()
```

## 🌐 JavaScript Client Examples

### Using EventSource (Browser)

```javascript
class RPAClient {
    constructor(baseUrl, token) {
        this.baseUrl = baseUrl.replace(/\/$/, '');
        this.token = token;
    }

    async executeTask(projectId, params = {}, execPosition = 'EXECUTOR', sid = '') {
        const url = `${this.baseUrl}/rpa/v1/exec`;

        const payload = {
            project_id: projectId,
            exec_position: execPosition,
            params: params,
            sid: sid
        };

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();

            return {
                async *[Symbol.asyncIterator]() {
                    try {
                        while (true) {
                            const { done, value } = await reader.read();
                            if (done) break;

                            const chunk = decoder.decode(value, { stream: true });
                            const lines = chunk.split('\n');

                            for (const line of lines) {
                                if (line.startsWith('data: ')) {
                                    try {
                                        const data = JSON.parse(line.substring(6));
                                        yield data;
                                    } catch (e) {
                                        console.warn('Failed to parse SSE data:', line);
                                    }
                                }
                            }
                        }
                    } finally {
                        reader.releaseLock();
                    }
                }
            };
        } catch (error) {
            throw new Error(`Request failed: ${error.message}`);
        }
    }
}

// Usage Example
async function main() {
    const client = new RPAClient('http://localhost:19999', 'your-token-here');

    try {
        const stream = await client.executeTask(
            'test-project-123',
            {
                action: 'web_automation',
                target: 'https://example.com'
            },
            'EXECUTOR',
            'js-session-id'
        );

        for await (const event of stream) {
            console.log('Received event:', event);

            if (event.code === 0) {
                console.log('✅ Task executed successfully');
                console.log('Result:', event.data);
                break;
            } else if (event.code !== 0) {
                console.log('❌ Task execution failed:', event.message);
                break;
            }
        }
    } catch (error) {
        console.error('Request failed:', error);
    }
}

// Run example
main();
```

### Node.js Client

```javascript
const https = require('https');
const http = require('http');

class NodeRPAClient {
    constructor(baseUrl, token) {
        this.baseUrl = baseUrl.replace(/\/$/, '');
        this.token = token;
    }

    executeTask(projectId, params = {}, execPosition = 'EXECUTOR', sid = '') {
        return new Promise((resolve, reject) => {
            const url = new URL(`${this.baseUrl}/rpa/v1/exec`);
            const payload = JSON.stringify({
                project_id: projectId,
                exec_position: execPosition,
                params: params,
                sid: sid
            });

            const options = {
                hostname: url.hostname,
                port: url.port,
                path: url.pathname,
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.token}`,
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(payload)
                }
            };

            const client = url.protocol === 'https:' ? https : http;
            const req = client.request(options, (res) => {
                if (res.statusCode !== 200) {
                    reject(new Error(`HTTP ${res.statusCode}: ${res.statusMessage}`));
                    return;
                }

                const events = [];
                let buffer = '';

                res.on('data', (chunk) => {
                    buffer += chunk.toString();
                    const lines = buffer.split('\n');
                    buffer = lines.pop(); // Keep incomplete lines

                    for (const line of lines) {
                        if (line.startsWith('data: ')) {
                            try {
                                const data = JSON.parse(line.substring(6));
                                events.push(data);
                            } catch (e) {
                                console.warn('Failed to parse SSE data:', line);
                            }
                        }
                    }
                });

                res.on('end', () => {
                    resolve(events);
                });
            });

            req.on('error', reject);
            req.write(payload);
            req.end();
        });
    }
}

// Usage Example
async function main() {
    const client = new NodeRPAClient('http://localhost:19999', 'your-token-here');

    try {
        const events = await client.executeTask(
            'test-project-123',
            { action: 'node_automation' },
            'EXECUTOR',
            'node-session-id'
        );

        for (const event of events) {
            console.log('Event:', event);

            if (event.code === 0) {
                console.log('Task completed successfully');
                break;
            } else if (event.code !== 0) {
                console.log('Task execution failed');
                break;
            }
        }
    } catch (error) {
        console.error('Request failed:', error);
    }
}

main();
```

## 🔧 cURL Examples

### Basic Request

```bash
curl -X POST "http://localhost:19999/rpa/v1/exec" \
  -H "Authorization: Bearer your-token-here" \
  -H "Content-Type: application/json" \
  -d '{
    "project_id": "test-project-123",
    "exec_position": "EXECUTOR",
    "params": {
      "action": "test_automation",
      "target": "web"
    },
    "sid": "curl-session-id"
  }'
```

### Request with Timeout

```bash
curl -X POST "http://localhost:19999/rpa/v1/exec" \
  -H "Authorization: Bearer your-token-here" \
  -H "Content-Type: application/json" \
  -m 600 \
  --no-buffer \
  -d '{
    "project_id": "long-running-task",
    "exec_position": "EXECUTOR",
    "params": {
      "action": "batch_process",
      "items": 1000
    },
    "sid": "long-session-id"
  }'
```

### Using Environment Variables

```bash
# Set environment variables
export RPA_SERVER_URL="http://localhost:19999"
export RPA_TOKEN="your-token-here"

# Request using environment variables
curl -X POST "${RPA_SERVER_URL}/rpa/v1/exec" \
  -H "Authorization: Bearer ${RPA_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "project_id": "env-test-project",
    "params": {"env": "production"}
  }'
```

## ❌ Error Handling

### Common Error Codes

| Error Code | Description | Solution |
|-----------|-------------|----------|
| 0 | Success | Handle result normally |
| 55001 | Task creation error | Check request parameters and RPA API configuration |
| 55002 | Task query error | Check task ID and network connection |
| 55003 | Timeout error | Increase timeout or check task complexity |
| 55999 | Unknown error | Check detailed error information and logs |

### Python Error Handling Example

```python
import asyncio
import httpx
import json

async def robust_execute_task(client, project_id, max_retries=3):
    """Task execution with retry mechanism"""
    for attempt in range(max_retries):
        try:
            async for event in client.execute_task(project_id):
                code = event.get("code")
                message = event.get("message", "")

                if code == 0:  # Success
                    return event.get("data")
                elif code == 55003:  # Timeout
                    print(f"Task timeout, attempting retry ({attempt + 1}/{max_retries})")
                    break
                elif code in [55001, 55002]:  # Task creation/query error
                    print(f"Task execution error: {message}")
                    if "Invalid project" in message:
                        raise ValueError(f"Invalid project ID: {project_id}")
                    break
                else:  # Other errors
                    print(f"Unknown error (code: {code}): {message}")
                    break
        except httpx.RequestError as e:
            print(f"Network request error: {e}")
            if attempt < max_retries - 1:
                await asyncio.sleep(2 ** attempt)  # Exponential backoff
            else:
                raise
        except json.JSONDecodeError as e:
            print(f"JSON parsing error: {e}")
            break

    raise Exception(f"Task execution failed after {max_retries} retries")

# Usage Example
async def main():
    client = RPAClient("http://localhost:19999", "your-token")

    try:
        result = await robust_execute_task(client, "test-project-123")
        print(f"Task completed successfully: {result}")
    except Exception as e:
        print(f"Final failure: {e}")

asyncio.run(main())
```

## 🚀 Advanced Usage

### Batch Task Execution

```python
import asyncio
import aiohttp
import json

class BatchRPAClient:
    def __init__(self, base_url: str, token: str, max_concurrent: int = 5):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.semaphore = asyncio.Semaphore(max_concurrent)
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

    async def execute_single_task(self, session, task_config):
        """Execute single task"""
        async with self.semaphore:
            url = f"{self.base_url}/rpa/v1/exec"

            try:
                async with session.post(url, headers=self.headers, json=task_config) as response:
                    if response.status != 200:
                        return {
                            "task_id": task_config.get("sid"),
                            "status": "failed",
                            "error": f"HTTP {response.status}"
                        }

                    async for line in response.content:
                        line = line.decode('utf-8').strip()
                        if line.startswith("data: "):
                            try:
                                data = json.loads(line[6:])
                                if data.get("code") == 0:
                                    return {
                                        "task_id": task_config.get("sid"),
                                        "status": "completed",
                                        "result": data.get("data")
                                    }
                                elif data.get("code") != 0:
                                    return {
                                        "task_id": task_config.get("sid"),
                                        "status": "failed",
                                        "error": data.get("message")
                                    }
                            except json.JSONDecodeError:
                                continue

            except Exception as e:
                return {
                    "task_id": task_config.get("sid"),
                    "status": "error",
                    "error": str(e)
                }

    async def execute_batch(self, task_configs):
        """Execute batch tasks"""
        async with aiohttp.ClientSession() as session:
            tasks = [
                self.execute_single_task(session, config)
                for config in task_configs
            ]

            return await asyncio.gather(*tasks, return_exceptions=True)

# Usage Example
async def batch_example():
    client = BatchRPAClient("http://localhost:19999", "your-token", max_concurrent=3)

    # Define batch tasks
    tasks = [
        {
            "project_id": "project-1",
            "params": {"action": "task1"},
            "sid": "batch-task-1"
        },
        {
            "project_id": "project-2",
            "params": {"action": "task2"},
            "sid": "batch-task-2"
        },
        {
            "project_id": "project-3",
            "params": {"action": "task3"},
            "sid": "batch-task-3"
        }
    ]

    # Execute batch tasks
    results = await client.execute_batch(tasks)

    # Process results
    for result in results:
        if isinstance(result, Exception):
            print(f"Task exception: {result}")
        else:
            print(f"Task {result['task_id']}: {result['status']}")
            if result['status'] == 'completed':
                print(f"  Result: {result['result']}")
            elif result['status'] in ['failed', 'error']:
                print(f"  Error: {result['error']}")

asyncio.run(batch_example())
```

### Task Progress Monitoring

```python
import asyncio
import time
from datetime import datetime

class ProgressMonitor:
    def __init__(self, client):
        self.client = client
        self.start_time = None
        self.last_update = None

    async def execute_with_progress(self, project_id, params=None, sid=None):
        """Task execution with progress monitoring"""
        self.start_time = time.time()
        self.last_update = self.start_time

        print(f"🚀 Starting task execution: {project_id}")
        print(f"⏰ Start time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

        try:
            async for event in self.client.execute_task(project_id, params, sid=sid):
                current_time = time.time()
                elapsed = current_time - self.start_time

                print(f"\n📊 Task status update (elapsed: {elapsed:.2f}s)")
                print(f"   Code: {event.get('code')}")
                print(f"   Message: {event.get('message')}")

                if event.get('data'):
                    print(f"   Data: {event.get('data')}")

                # Check task completion
                if event.get("code") == 0:
                    print(f"\n✅ Task completed! Total time: {elapsed:.2f}s")
                    return event.get("data")
                elif event.get("code") != 0:
                    print(f"\n❌ Task failed! Elapsed time: {elapsed:.2f}s")
                    raise Exception(f"Task failed: {event.get('message')}")

                self.last_update = current_time

        except Exception as e:
            elapsed = time.time() - self.start_time
            print(f"\n💥 Task exception! Elapsed time: {elapsed:.2f}s")
            print(f"   Error: {e}")
            raise

# Usage Example
async def monitor_example():
    client = RPAClient("http://localhost:19999", "your-token")
    monitor = ProgressMonitor(client)

    try:
        result = await monitor.execute_with_progress(
            project_id="complex-task",
            params={
                "action": "data_processing",
                "items_count": 1000,
                "batch_size": 50
            },
            sid="monitor-session"
        )
        print(f"🎉 Final result: {result}")

    except Exception as e:
        print(f"💔 Execution failed: {e}")

asyncio.run(monitor_example())
```

---

## 📞 Need Help?

- 🐛 **Issue Reports**: [GitHub Issues](https://github.com/your-org/xingchen-rpa-server/issues)
- 📖 **Detailed Documentation**: [README.md](./README.md)
- 🧪 **Testing Guide**: [TEST_SUMMARY.md](./TEST_SUMMARY.md)

These examples cover common use cases and can be adjusted and extended based on your specific requirements.