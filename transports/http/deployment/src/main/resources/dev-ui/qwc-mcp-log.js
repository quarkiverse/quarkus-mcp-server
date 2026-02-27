import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import { JsonRpc } from 'jsonrpc';
import { msg, updateWhenLocaleChanges } from 'localization';

/**
 * This component shows the MCP Server request/response log in the Dev UI footer.
 */
export class QwcMcpLog extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
            font-family: monospace;
            font-size: 12px;
        }
        .toolbar {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 5px 10px;
            border-bottom: 1px solid var(--lumo-contrast-10pct);
            background: var(--lumo-contrast-5pct);
        }
        .log-container {
            flex: 1;
            overflow-y: auto;
            padding: 5px;
        }
        .log-entry {
            display: flex;
            gap: 10px;
            padding: 4px 8px;
            border-bottom: 1px solid var(--lumo-contrast-5pct);
        }
        .log-entry:hover {
            background: var(--lumo-contrast-5pct);
        }
        .log-entry.request {
            color: var(--lumo-primary-text-color);
        }
        .log-entry.response {
            color: var(--lumo-success-text-color);
        }
        .log-entry.error {
            color: var(--lumo-error-text-color);
        }
        .timestamp {
            color: var(--lumo-secondary-text-color);
            white-space: nowrap;
        }
        .type {
            width: 60px;
            font-weight: bold;
        }
        .method {
            width: 180px;
            color: var(--lumo-primary-color);
        }
        .server {
            width: 100px;
            color: var(--lumo-secondary-text-color);
        }
        .details {
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            cursor: pointer;
        }
        .details:hover {
            text-decoration: underline;
        }
        .count {
            color: var(--lumo-secondary-text-color);
        }
        .empty-message {
            color: var(--lumo-secondary-text-color);
            font-style: italic;
            padding: 20px;
            text-align: center;
        }
    `;

    static properties = {
        _logs: { state: true, type: Array },
        _expandedIndex: { state: true, type: Number }
    };

    constructor() {
        super();
        updateWhenLocaleChanges(this);
        this._logs = [];
        this._expandedIndex = -1;
        this._observer = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._startLogStream();
    }

    disconnectedCallback() {
        if (this._observer) {
            this._observer.cancel();
        }
        super.disconnectedCallback();
    }

    _startLogStream() {
        this._observer = this.jsonRpc.streamLog().onNext(response => {
            this._logs = [...this._logs, response.result];
            // Auto-scroll to bottom
            this.updateComplete.then(() => {
                const container = this.shadowRoot.querySelector('.log-container');
                if (container) {
                    container.scrollTop = container.scrollHeight;
                }
            });
        });
    }

    _clearLog() {
        this.jsonRpc.clearLog();
        this._logs = [];
    }

    _toggleExpand(index) {
        this._expandedIndex = this._expandedIndex === index ? -1 : index;
    }

    _formatDetails(log) {
        if (log.type === 'request') {
            return JSON.stringify(log.params);
        } else if (log.type === 'response') {
            return JSON.stringify(log.response);
        }
        return '';
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-button theme="small tertiary" @click="${this._clearLog}">
                    <vaadin-icon icon="font-awesome-solid:trash" slot="prefix"></vaadin-icon>
                    ${msg('Clear', { id: 'mcp-server-log-clear' })}
                </vaadin-button>
                <span class="count">${this._logs.length} ${msg('entries', { id: 'mcp-server-log-entries' })}</span>
            </div>
            <div class="log-container">
                ${this._logs.length === 0
                    ? html`<div class="empty-message">${msg('No MCP requests yet. Use the Tools, Prompts, or Resources pages to make requests.', { id: 'mcp-server-log-empty' })}</div>`
                    : this._logs.map((log, index) => this._renderLogEntry(log, index))}
            </div>
        `;
    }

    _renderLogEntry(log, index) {
        const isExpanded = this._expandedIndex === index;
        const hasError = log.response && log.response.error;
        const entryClass = hasError ? 'error' : log.type;

        return html`
            <div class="log-entry ${entryClass}">
                <span class="timestamp">${log.timestamp}</span>
                <span class="type">${log.type === 'request' ? 'REQ' : 'RES'}</span>
                <span class="method">${log.method}</span>
                <span class="server">${log.serverName}</span>
                <span class="details" @click="${() => this._toggleExpand(index)}" title="Click to expand">
                    ${isExpanded
                        ? html`<pre style="white-space: pre-wrap; margin: 0;">${JSON.stringify(log.type === 'request' ? log.params : log.response, null, 2)}</pre>`
                        : this._formatDetails(log)}
                </span>
            </div>
        `;
    }
}

customElements.define('qwc-mcp-log', QwcMcpLog);
