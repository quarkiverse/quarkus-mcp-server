
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/text-field';
import '@vaadin/checkbox';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/vertical-layout';
import '@vaadin/icon';
import { dialogHeaderRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import 'qui-themed-code-block';
import '@qomponent/qui-badge';
import { JsonRpc } from 'jsonrpc';
import { msg, updateWhenLocaleChanges } from 'localization';

/**
 * This component shows the MCP prompts.
 */
export class QwcMcpPrompts extends LitElement {

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
        `;

    static properties = {
        _prompts: { state: true },
        _filtered: { state: true, type: Array },
        _selectedPrompt: { state: true },
        _showInputDialog: { state: true, type: Boolean },
        _inputValues: { type: Object },
        _promptResult: { state: true },
        _searchTerm: { state: true },
        _forceNewSession: { state: true, type: Boolean },
        _bearerToken: { state: true, type: String }
    };

    constructor() {
        super();
        updateWhenLocaleChanges(this);
        this._selectedPrompt = null;
        this._showInputDialog = false;
        this._inputValues = new Map();
        this._promptResult = null;
        this._prompts = null;
        this._filtered = null;
        this._searchTerm = '';
        this._forceNewSession = false;
        this._bearerToken = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadPrompts();
        this._inputValues.clear();
    }

    render() {
        if (this._prompts) {
            return html`${this._renderResultDialog()}
                        ${this._renderInputDialog()}
                        ${this._renderGrid()}`;
        } else {
            return html`
            <div style="color: var(--lumo-secondary-text-color);width: 95%;">
                <div>${msg('Fetching prompts...', { id: 'mcp-server-fetching-prompts' })}</div>
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
                                this._selectedPrompt = item;
                                if (item.arguments && item.arguments.length > 0) {
                                    this._showInputDialog = true;
                                } else {
                                    this._getPrompt();
                                }
                            }
                        }}">
                        <vaadin-grid-sort-column
                            header="${msg('Name', { id: 'mcp-server-col-name' })}"
                            path="name"
                            auto-width
                            ${columnBodyRenderer(this._renderName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="${msg('MCP Server', { id: 'mcp-server-col-mcp-server' })}"
                            path="serverName"
                            auto-width
                            ${columnBodyRenderer(this._renderServerName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="${msg('Description', { id: 'mcp-server-col-description' })}"
                            path="description"
                            auto-width>
                        </vaadin-grid-sort-column>
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
                        header-title="${msg('Prompt result', { id: 'mcp-server-prompt-result' })}"
                        .opened="${this._promptResult !== null}"
                        @opened-changed="${(event) => {
                            if (!event.detail.value) {
                                this._promptResult = null;
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
                        ${dialogRenderer(() => this._renderPromptResult())}
                    ></vaadin-dialog>`;
    }

    _renderInputDialog() {
        return html`<vaadin-dialog
                        header-title="${msg('Input', { id: 'mcp-server-input' })}"
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
                        ${dialogRenderer(() => this._renderPromptInput())}
                    ></vaadin-dialog>`;
    }

    _renderPromptResult() {
        return html`<div class="codeBlock">
                        <qui-themed-code-block
                            mode='json'
                            content='${this._promptResult}'
                            showLineNumbers>
                        </qui-themed-code-block>
                    </div>`;
    }

    _renderPromptInput() {
        if (this._selectedPrompt) {
            const prompt = this._selectedPrompt;
            const args = prompt.arguments || [];

            return html`<vaadin-vertical-layout>
                            <b>${prompt.name}</b>
                            <vaadin-checkbox
                                id="prompt_force_new_session"
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
                           ${args.map(
                                (arg) => html`
                                  <vaadin-text-field
                                    label="${arg.name}"
                                    helper-text="${arg.description || ''}"
                                    ?required="${arg.required}"
                                    @input=${(e) => this._updateInputValue(prompt.name, arg.name, e)}
                                    @blur=${(e) => this._updateInputValue(prompt.name, arg.name, e)}
                                  ></vaadin-text-field>
                                `
                              )}
                              <vaadin-button theme="primary" @click="${() => this._getInputValuesAndGet()}">${msg('Get', { id: 'mcp-server-get' })}</vaadin-button>
                        </vaadin-vertical-layout>`;
        }
        return html``;
    }

    _renderName(prompt) {
        return html`<code>${prompt.name}</code>`;
    }

    _renderServerName(prompt) {
        return html`${prompt.serverName}`;
    }

    _renderArgsCount(prompt) {
        const argCount = prompt.arguments ? prompt.arguments.length : 0;
        if (argCount > 0) {
            const argNames = prompt.arguments.map(a => a.name).join(', ');
            return html`<qui-badge pill title="${argNames}"><span>${argCount}</span></qui-badge>`;
        }
        return html``;
    }

    _filterTextChanged(e) {
        this._searchTerm = (e.detail.value || '').trim();
        return this._filterGrid();
    }

    _filterGrid() {
        if (this._searchTerm === '') {
            this._filtered = this._prompts;
            return;
        }

        this._filtered = this._prompts.filter((prompt) => {
           return this._match(prompt.name, this._searchTerm) ||
                  this._match(prompt.description, this._searchTerm) ||
                  this._match(prompt.serverName, this._searchTerm);
        });
    }

    _match(value, term) {
        if (!value) {
            return false;
        }
        return value.toLowerCase().includes(term.toLowerCase());
    }

    _closeResultDialog() {
        this._promptResult = null;
    }

    _closeInputDialog() {
        this._showInputDialog = false;
    }

    _updateInputValue(promptName, argName, e) {
        let params = new Map();
        if (this._inputValues.has(promptName)) {
            params = this._inputValues.get(promptName);
        }
        params.set(argName, e.target.value);
        this._inputValues.set(promptName, params);
    }

    _getInputValuesAndGet() {
        if (this._selectedPrompt) {
            this._getPrompt();
        }
    }

    _getPrompt() {
        if (!this._selectedPrompt) return;

        const prompt = this._selectedPrompt;
        let argsObj = {};

        if (this._inputValues.has(prompt.name)) {
            argsObj = Object.fromEntries(this._inputValues.get(prompt.name));
        }

        this.jsonRpc.getPrompt({
            name: prompt.name,
            args: JSON.stringify(argsObj),
            bearerToken: this._bearerToken,
            forceNewSession: this._forceNewSession
        }).then(jsonRpcResponse => {
            this._setPromptResult(jsonRpcResponse.result.response);
        });
    }

    _setPromptResult(result) {
        if (this._isJsonSerializable(result)) {
            this._promptResult = JSON.stringify(result, null, 2);
        } else {
            this._promptResult = result;
        }
    }

    _loadPrompts() {
        this.jsonRpc.getPromptsData()
            .then(jsonResponse => {
                this._prompts = jsonResponse.result;
                this._filtered = this._prompts;
            });
    }

    _isJsonSerializable(value) {
        return value !== null && (typeof value === 'object');
    }

}
customElements.define('qwc-mcp-prompts', QwcMcpPrompts);
