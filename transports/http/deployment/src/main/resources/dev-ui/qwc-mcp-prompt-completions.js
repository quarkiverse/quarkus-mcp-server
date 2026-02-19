
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
 * This component shows the MCP prompt completions.
 */
export class QwcMcpPromptCompletions extends LitElement {

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
        _completions: { state: true },
        _filtered: { state: true, type: Array },
        _selectedCompletion: { state: true },
        _showInputDialog: { state: true, type: Boolean },
        _completionResult: { state: true },
        _searchTerm: { state: true },
        _forceNewSession: { state: true, type: Boolean },
        _bearerToken: { state: true, type: String },
        _argumentValue: { state: true, type: String }
    };

    constructor() {
        super();
        updateWhenLocaleChanges(this);
        this._selectedCompletion = null;
        this._showInputDialog = false;
        this._completionResult = null;
        this._completions = null;
        this._filtered = null;
        this._searchTerm = '';
        this._forceNewSession = false;
        this._bearerToken = '';
        this._argumentValue = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadCompletions();
    }

    render() {
        if (this._completions) {
            return html`${this._renderResultDialog()}
                        ${this._renderInputDialog()}
                        ${this._renderGrid()}`;
        } else {
            return html`
            <div style="color: var(--lumo-secondary-text-color);width: 95%;">
                <div>${msg('Fetching prompt completions...', { id: 'mcp-server-fetching-prompt-completions' })}</div>
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
                                this._selectedCompletion = item;
                                this._argumentValue = '';
                                this._showInputDialog = true;
                            }
                        }}">
                        <vaadin-grid-sort-column
                            header="${msg('Prompt', { id: 'mcp-server-col-prompt' })}"
                            path="name"
                            auto-width
                            ${columnBodyRenderer(this._renderName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="${msg('Completed argument', { id: 'mcp-server-col-completed-argument' })}"
                            path="argumentName"
                            auto-width
                            ${columnBodyRenderer(this._renderArgName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="${msg('MCP Server', { id: 'mcp-server-col-mcp-server' })}"
                            path="serverName"
                            auto-width
                            ${columnBodyRenderer(this._renderServerName, [])}
                        ></vaadin-grid-sort-column>
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
                        header-title="${msg('Completion result', { id: 'mcp-server-completion-result' })}"
                        .opened="${this._completionResult !== null}"
                        @opened-changed="${(event) => {
                            if (!event.detail.value) {
                                this._completionResult = null;
                            }
                        }}"
                        ${dialogHeaderRenderer(
                            () => html`
                              <vaadin-button theme="tertiary" @click="${this._closeDialogs}">
                                <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                              </vaadin-button>
                            `,
                            []
                        )}
                        ${dialogRenderer(() => this._renderCompletionResult())}
                    ></vaadin-dialog>`;
    }

    _renderInputDialog() {
        return html`<vaadin-dialog
                        header-title="${msg('Complete prompt', { id: 'mcp-server-complete-prompt' })}"
                        .opened="${this._showInputDialog}"
                        @opened-changed="${(event) => {
                            if (!event.detail.value) {
                                this._showInputDialog = false;
                            }
                        }}"
                        ${dialogHeaderRenderer(
                            () => html`
                              <vaadin-button theme="tertiary" @click="${this._closeDialogs}">
                                <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                              </vaadin-button>
                            `,
                            []
                        )}
                        ${dialogRenderer(() => this._renderCompletionInput())}
                    ></vaadin-dialog>`;
    }

    _renderCompletionResult() {
        return html`<div class="codeBlock">
                        <qui-themed-code-block
                            mode='json'
                            content='${this._completionResult}'
                            showLineNumbers>
                        </qui-themed-code-block>
                    </div>`;
    }

    _renderCompletionInput() {
        if (this._selectedCompletion) {
            const completion = this._selectedCompletion;

            return html`<vaadin-vertical-layout>
                            <b>${completion.name}</b>
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
                            <vaadin-text-field
                                label="${completion.argumentName}"
                                .value="${this._argumentValue}"
                                style="width: 100%;"
                                @input="${(e) => this._argumentValue = e.target.value}">
                            </vaadin-text-field>
                            <vaadin-button theme="primary" @click="${() => this._completePrompt()}">${msg('Complete', { id: 'mcp-server-complete' })}</vaadin-button>
                        </vaadin-vertical-layout>`;
        }
        return html``;
    }

    _renderName(completion) {
        return html`<code>${completion.name}</code>`;
    }

    _renderArgName(completion) {
        return html`<code>${completion.argumentName}</code>`;
    }

    _renderServerName(completion) {
        return html`${completion.serverName}`;
    }

    _filterTextChanged(e) {
        this._searchTerm = (e.detail.value || '').trim();
        return this._filterGrid();
    }

    _filterGrid() {
        if (this._searchTerm === '') {
            this._filtered = this._completions;
            return;
        }

        this._filtered = this._completions.filter((completion) => {
           return this._match(completion.name, this._searchTerm) ||
                  this._match(completion.argumentName, this._searchTerm) ||
                  this._match(completion.serverName, this._searchTerm);
        });
    }

    _match(value, term) {
        if (!value) {
            return false;
        }
        return value.toLowerCase().includes(term.toLowerCase());
    }

    _closeDialogs() {
        this._completionResult = null;
        this._showInputDialog = false;
    }

    _completePrompt() {
        if (!this._selectedCompletion) return;

        this._showInputDialog = false;
        const completion = this._selectedCompletion;

        this.jsonRpc.completePrompt({
            name: completion.name,
            argumentName: completion.argumentName,
            argumentValue: this._argumentValue,
            bearerToken: this._bearerToken,
            forceNewSession: this._forceNewSession
        }).then(jsonRpcResponse => {
            this._setCompletionResult(jsonRpcResponse.result.response);
        });
    }

    _setCompletionResult(result) {
        if (this._isJsonSerializable(result)) {
            this._completionResult = JSON.stringify(result, null, 2);
        } else {
            this._completionResult = result;
        }
    }

    _loadCompletions() {
        this.jsonRpc.getPromptCompletionsData()
            .then(jsonResponse => {
                this._completions = jsonResponse.result;
                this._filtered = this._completions;
            });
    }

    _isJsonSerializable(value) {
        return value !== null && (typeof value === 'object');
    }

}
customElements.define('qwc-mcp-prompt-completions', QwcMcpPromptCompletions);
