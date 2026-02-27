
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/number-field';
import '@vaadin/checkbox';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/vertical-layout';
import '@vaadin/icon';
import '@vaadin/tabs';
import '@vaadin/tabsheet';
import { dialogHeaderRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import 'qui-themed-code-block';
import '@qomponent/qui-badge';
import { JsonRpc } from 'jsonrpc';
import { msg, updateWhenLocaleChanges } from 'localization';
import { notifier } from 'notifier';

/**
 * This component shows the MCP tools.
 */
export class QwcMcpTools extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
       :host {
          display: flex;
          flex-direction: column;
          gap: 10px;
        }
        .grid {
          display: flex;
          flex-direction: column;
          padding-left: 5px;
          padding-right: 5px;
          max-width: 100%;
        }
        code {
          font-size: 85%;
        }
        .filterText {
          width: 100%;
        }
        vaadin-grid::part(row):hover {
          cursor: pointer;
          background-color: var(--lumo-contrast-5pct);
        }
        vaadin-grid::part(body-cell):hover {
          cursor: pointer;
        }
        .noArgsMessage {
          color: var(--lumo-secondary-text-color);
          font-style: italic;
          padding: var(--lumo-space-m) 0;
        }
        vaadin-tabsheet {
          width: 100%;
        }
        `;

    static properties = {
        _tools: { state: true },
        _filtered: { state: true, type: Array },
        _selectedTool: { state: true },
        _showInputDialog: { state: true, type: Boolean },
        _inputValues: { type: Object },
        _rawJsonInput: { state: true, type: String },
        _useRawJson: { state: true, type: Boolean },
        _toolResult: { state: true },
        _searchTerm: { state: true },
        _forceNewSession: { state: true, type: Boolean },
        _bearerToken: { state: true, type: String }
    };

    constructor() {
        super();
        updateWhenLocaleChanges(this);
        this._selectedTool = null;
        this._showInputDialog = false;
        this._inputValues = new Map();
        this._rawJsonInput = '{}';
        this._useRawJson = false;
        this._toolResult = null;
        this._tools = null;
        this._filtered = null;
        this._searchTerm = '';
        this._forceNewSession = false;
        this._bearerToken = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadTools();
        this._inputValues.clear();
    }

    render() {
        if (this._tools) {
            return html`${this._renderResultDialog()}
                        ${this._renderInputDialog()}
                        ${this._renderGrid()}`;
        } else {
            return html`
            <div style="color: var(--lumo-secondary-text-color);width: 95%;">
                <div>${msg('Fetching tools...', { id: 'mcp-server-fetching-tools' })}</div>
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            </div>
            `;
        }
    }

    _renderGrid() {
        return html`<div class="grid">
                    ${this._renderFilterTextbar()}
                    <vaadin-grid .items="${this._filtered}" theme="row-stripes no-border" all-rows-visible
                        @active-item-changed="${(e) => {
                            const item = e.detail.value;
                            if (item) {
                                this._selectedTool = item;
                                this._rawJsonInput = this._generateDefaultJson(item);
                                this._showInputDialog = true;
                            }
                        }}">
                        <vaadin-grid-sort-column
                            header="${msg('Name', { id: 'mcp-server-col-name' })}"
                            path="name"
                            auto-width
                            ${columnBodyRenderer(this._renderName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="${msg('Description', { id: 'mcp-server-col-description' })}"
                            path="description"
                            auto-width>
                        </vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="${msg('MCP Server', { id: 'mcp-server-col-mcp-server' })}"
                            path="serverName"
                            auto-width
                            ${columnBodyRenderer(this._renderServerName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-column
                            header="${msg('Args', { id: 'mcp-server-col-args' })}"
                            frozen-to-end
                            auto-width
                            flex-grow="0"
                            ${columnBodyRenderer(this._renderArgsCount, [])}
                        ></vaadin-grid-column>
                    </vaadin-grid>
                </div>`;
    }

    _renderFilterTextbar() {
        return html`<vaadin-text-field class="filterText"
                            placeholder="${msg('Filter', { id: 'mcp-server-filter' })}"
                            style="flex: 1;"
                            @value-changed="${(e) => this._filterTextChanged(e)}">
                        <vaadin-icon slot="prefix" icon="font-awesome-solid:filter"></vaadin-icon>
                        <qui-badge slot="suffix"><span>${this._filtered?.length}</span></qui-badge>
                    </vaadin-text-field>`;
    }

    _renderResultDialog() {
        return html`<vaadin-dialog
                        header-title="${msg('Tool call result', { id: 'mcp-server-tool-call-result' })}"
                        .opened="${this._toolResult !== null}"
                        @opened-changed="${(event) => {
                            if (!event.detail.value) {
                                this._toolResult = null;
                            }
                        }}"
                        ${dialogHeaderRenderer(
                            () => html`
                              <vaadin-button theme="tertiary" @click="${this._closeResultDialog}">
                                <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                              </vaadin-button>
                            `,
                            []
                        )}
                        ${dialogRenderer(() => this._renderToolResult())}
                    ></vaadin-dialog>`;
    }

    _renderInputDialog() {
        return html`<vaadin-dialog
                        header-title="${msg('Tool call input', { id: 'mcp-server-tool-call-input' })}"
                        .opened="${this._showInputDialog}"
                        @opened-changed="${(event) => {
                            if (!event.detail.value) {
                                this._showInputDialog = false;
                            }
                        }}"
                        ${dialogHeaderRenderer(
                            () => html`
                              <vaadin-button theme="tertiary" @click="${this._closeInputDialog}">
                                <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                              </vaadin-button>
                            `,
                            []
                        )}
                        ${dialogRenderer(() => this._renderToolInput(), [this._selectedTool, this._useRawJson, this._rawJsonInput])}
                    ></vaadin-dialog>`;
    }

    _renderToolResult() {
        return html`<div class="codeBlock">
                        <qui-themed-code-block
                            mode='json'
                            content='${this._toolResult}'
                            showLineNumbers>
                        </qui-themed-code-block>
                    </div>`;
    }

    _renderToolInput() {
        if (this._selectedTool) {
            const tool = this._selectedTool;
            const args = tool.args || [];
            const hasArgs = args.length > 0;

            return html`<vaadin-vertical-layout style="min-width: 400px;">
                            <b>${tool.name}</b>
                            <vaadin-checkbox
                                id="tool_force_new_session"
                                label="${msg('Force new session', { id: 'mcp-server-force-new-session' })}"
                                helper-text="${msg('Initialize a new MCP session for the request', { id: 'mcp-server-force-new-session-helper' })}"
                                .checked="${this._forceNewSession}"
                                @change="${(e) => this._forceNewSession = e.target.checked}">
                            </vaadin-checkbox>
                            <vaadin-text-field
                                label="${msg('Bearer Token', { id: 'mcp-server-bearer-token' })}"
                                .value="${this._bearerToken}"
                                clear-button-visible
                                style="width: 100%;"
                                helper-text="${msg('The Authorization header with the bearer token is automatically added to the HTTP POST request', { id: 'mcp-server-bearer-token-helper' })}"
                                @input="${(e) => this._bearerToken = e.target.value}">
                            </vaadin-text-field>
                            ${hasArgs ? this._renderArgsInput(tool, args) : this._renderNoArgs()}
                            <vaadin-button theme="primary" @click="${() => this._getInputValuesAndCall()}">${msg('Call', { id: 'mcp-server-call' })}</vaadin-button>
                        </vaadin-vertical-layout>`;
        }
        return html``;
    }

    _renderNoArgs() {
        return html`<div class="noArgsMessage">${msg('This tool has no parameters.', { id: 'mcp-server-no-parameters' })}</div>`;
    }

    _renderArgsInput(tool, args) {
        return html`
            <vaadin-tabsheet style="width: 100%;">
                <vaadin-tabs slot="tabs">
                    <vaadin-tab id="form-tab" @click="${() => this._useRawJson = false}">${msg('Form', { id: 'mcp-server-tab-form' })}</vaadin-tab>
                    <vaadin-tab id="json-tab" @click="${() => this._useRawJson = true}">${msg('JSON', { id: 'mcp-server-tab-json' })}</vaadin-tab>
                </vaadin-tabs>
                <div tab="form-tab" ?hidden="${this._useRawJson}">
                    ${args.map((arg) => this._renderArgInput(tool.name, arg))}
                </div>
                <div tab="json-tab" ?hidden="${!this._useRawJson}">
                    <vaadin-text-area
                        label="${msg('Arguments (JSON)', { id: 'mcp-server-args-json' })}"
                        style="width: 100%; min-height: 150px; font-family: monospace;"
                        .value="${this._rawJsonInput}"
                        @input="${(e) => this._rawJsonInput = e.target.value}">
                    </vaadin-text-area>
                </div>
            </vaadin-tabsheet>
        `;
    }

    _renderArgInput(toolName, arg) {
        const argType = (arg.type || '').toLowerCase();

        if (argType === 'boolean') {
            return html`
                <vaadin-checkbox
                    label="${arg.name}${arg.required ? ' *' : ''}"
                    helper-text="${arg.description || ''}"
                    @change=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.checked, 'boolean')}>
                </vaadin-checkbox>
            `;
        } else if (argType === 'integer' || argType === 'number') {
            return html`
                <vaadin-number-field
                    label="${arg.name}${arg.required ? ' *' : ''}"
                    helper-text="${arg.description || ''}"
                    placeholder="${arg.type || ''}"
                    ?step-buttons-visible="${argType === 'integer'}"
                    step="${argType === 'integer' ? 1 : 'any'}"
                    @value-changed=${(e) => this._updateInputValueTyped(toolName, arg.name, e.detail.value, argType)}>
                </vaadin-number-field>
            `;
        } else if (argType === 'object' || argType === 'array') {
            return html`
                <vaadin-text-area
                    label="${arg.name}${arg.required ? ' *' : ''} (JSON)"
                    helper-text="${arg.description || ''}"
                    placeholder="${arg.type || ''}"
                    style="font-family: monospace;"
                    @input=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, argType)}>
                </vaadin-text-area>
            `;
        } else {
            return html`
                <vaadin-text-field
                    label="${arg.name}${arg.required ? ' *' : ''}"
                    helper-text="${arg.description || ''}"
                    placeholder="${arg.type || ''}"
                    @input=${(e) => this._updateInputValue(toolName, arg.name, e)}
                    @blur=${(e) => this._updateInputValue(toolName, arg.name, e)}>
                </vaadin-text-field>
            `;
        }
    }

    _renderName(tool) {
        return html`<code>${tool.name}</code>`;
    }

    _renderServerName(tool) {
        return html`${tool.serverName}`;
    }

    _renderArgsCount(tool) {
        const args = tool.args || [];
        return html`<qui-badge><span>${args.length}</span></qui-badge>`;
    }

    _generateDefaultJson(tool) {
        const args = tool.args || [];
        const obj = {};
        args.forEach(arg => {
            const type = (arg.type || '').toLowerCase();
            if (type === 'boolean') {
                obj[arg.name] = false;
            } else if (type === 'integer' || type === 'number') {
                obj[arg.name] = 0;
            } else if (type === 'array') {
                obj[arg.name] = [];
            } else if (type === 'object') {
                obj[arg.name] = {};
            } else {
                obj[arg.name] = '';
            }
        });
        return JSON.stringify(obj, null, 2);
    }

    _filterTextChanged(e) {
        this._searchTerm = (e.detail.value || '').trim();
        return this._filterGrid();
    }

    _filterGrid() {
        if (this._searchTerm === '') {
            this._filtered = this._tools;
            return;
        }

        this._filtered = this._tools.filter((tool) => {
           return this._match(tool.name, this._searchTerm) ||
                  this._match(tool.description, this._searchTerm) ||
                  this._match(tool.serverName, this._searchTerm);
        });
    }

    _match(value, term) {
        if (!value) {
            return false;
        }
        return value.toLowerCase().includes(term.toLowerCase());
    }

    _closeResultDialog() {
        this._toolResult = null;
    }

    _closeInputDialog() {
        this._showInputDialog = false;
    }

    _updateInputValue(toolName, argName, e) {
        let params = new Map();
        if (this._inputValues.has(toolName)) {
            params = this._inputValues.get(toolName);
        }
        params.set(argName, { value: e.target.value, type: 'string' });
        this._inputValues.set(toolName, params);
    }

    _updateInputValueTyped(toolName, argName, value, type) {
        let params = new Map();
        if (this._inputValues.has(toolName)) {
            params = this._inputValues.get(toolName);
        }
        params.set(argName, { value: value, type: type });
        this._inputValues.set(toolName, params);
    }

    _getInputValuesAndCall() {
        if (this._selectedTool) {
            this._callTool();
        }
    }

    _callTool() {
        if (!this._selectedTool) return;

        const tool = this._selectedTool;
        let argsJson;

        if (this._useRawJson) {
            // Use raw JSON input directly
            argsJson = this._rawJsonInput;
        } else {
            // Build from form inputs
            let argsObj = {};
            if (this._inputValues.has(tool.name)) {
                const params = this._inputValues.get(tool.name);
                for (const [key, entry] of params.entries()) {
                    argsObj[key] = this._convertValue(entry.value, entry.type);
                }
            }
            argsJson = JSON.stringify(argsObj);
        }

        this.jsonRpc.callTool({
            name: tool.name,
            args: argsJson,
            bearerToken: this._bearerToken,
            forceNewSession: this._forceNewSession
        }).then(jsonRpcResponse => {
            this._setToolResult(jsonRpcResponse.result.response);
        }).catch(error => {
            notifier.showErrorMessage(`Tool '${tool.name}' failed: ${error}`);
        });
    }

    _convertValue(value, type) {
        if (type === 'boolean') {
            return Boolean(value);
        } else if (type === 'integer') {
            if (value === '' || value === null || value === undefined) {
                return 0;
            }
            const parsed = parseInt(value, 10);
            return isNaN(parsed) ? 0 : parsed;
        } else if (type === 'number') {
            if (value === '' || value === null || value === undefined) {
                return 0;
            }
            const parsed = parseFloat(value);
            return isNaN(parsed) ? 0 : parsed;
        } else if (type === 'object' || type === 'array') {
            try {
                return JSON.parse(value);
            } catch (e) {
                return type === 'array' ? [] : {};
            }
        }
        return value;
    }

    _setToolResult(result) {
        if (this._isJsonSerializable(result)) {
            this._toolResult = JSON.stringify(result, null, 2);
        } else {
            this._toolResult = result;
        }
    }

    _loadTools() {
        this.jsonRpc.getToolsData()
            .then(jsonResponse => {
                this._tools = jsonResponse.result;
                this._filtered = this._tools;
            });
    }

    _isJsonSerializable(value) {
        return value !== null && (typeof value === 'object');
    }

}
customElements.define('qwc-mcp-tools', QwcMcpTools);
