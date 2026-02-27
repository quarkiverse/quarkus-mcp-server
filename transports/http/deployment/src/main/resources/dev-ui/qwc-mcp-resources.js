
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
 * This component shows the MCP resources.
 */
export class QwcMcpResources extends LitElement {

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
        _resources: { state: true },
        _filtered: { state: true, type: Array },
        _selectedResource: { state: true },
        _showInputDialog: { state: true, type: Boolean },
        _resourceResult: { state: true },
        _searchTerm: { state: true },
        _forceNewSession: { state: true, type: Boolean },
        _bearerToken: { state: true, type: String }
    };

    constructor() {
        super();
        updateWhenLocaleChanges(this);
        this._selectedResource = null;
        this._showInputDialog = false;
        this._resourceResult = null;
        this._resources = null;
        this._filtered = null;
        this._searchTerm = '';
        this._forceNewSession = false;
        this._bearerToken = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadResources();
    }

    render() {
        if (this._resources) {
            return html`${this._renderResultDialog()}
                        ${this._renderInputDialog()}
                        ${this._renderGrid()}`;
        } else {
            return html`
            <div style="color: var(--lumo-secondary-text-color);width: 95%;">
                <div>${msg('Fetching resources...', { id: 'mcp-server-fetching-resources' })}</div>
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
                                this._selectedResource = item;
                                this._showInputDialog = true;
                            }
                        }}">
                        <vaadin-grid-sort-column
                            header="${msg('URI', { id: 'mcp-server-col-uri' })}"
                            path="uri"
                            auto-width
                            ${columnBodyRenderer(this._renderUri, [])}
                        ></vaadin-grid-sort-column>
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
                        <vaadin-grid-sort-column
                            header="${msg('Mime type', { id: 'mcp-server-col-mime-type' })}"
                            path="mimeType"
                            auto-width
                            ${columnBodyRenderer(this._renderMimeType, [])}
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
                        header-title="${msg('Resource content', { id: 'mcp-server-resource-content' })}"
                        .opened="${this._resourceResult !== null}"
                        @opened-changed="${(event) => {
                            if (!event.detail.value) {
                                this._resourceResult = null;
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
                        ${dialogRenderer(() => this._renderResourceResult())}
                    ></vaadin-dialog>`;
    }

    _renderInputDialog() {
        return html`<vaadin-dialog
                        header-title="${msg('Read resource', { id: 'mcp-server-read-resource' })}"
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
                        ${dialogRenderer(() => this._renderResourceInput())}
                    ></vaadin-dialog>`;
    }

    _renderResourceResult() {
        return html`<div class="codeBlock">
                        <qui-themed-code-block
                            mode='json'
                            content='${this._resourceResult}'
                            showLineNumbers>
                        </qui-themed-code-block>
                    </div>`;
    }

    _renderResourceInput() {
        if (this._selectedResource) {
            const resource = this._selectedResource;

            return html`<vaadin-vertical-layout>
                            <b>${resource.name}</b>
                            <div><code>${resource.uri}</code></div>
                            <vaadin-checkbox
                                id="resource_force_new_session"
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
                            <vaadin-button theme="primary" @click="${() => this._readResource()}">${msg('Read', { id: 'mcp-server-read' })}</vaadin-button>
                        </vaadin-vertical-layout>`;
        }
        return html``;
    }

    _renderUri(resource) {
        return html`<code>${resource.uri}</code>`;
    }

    _renderName(resource) {
        return html`${resource.name}`;
    }

    _renderServerName(resource) {
        return html`${resource.serverName}`;
    }

    _renderMimeType(resource) {
        return html`<code>${resource.mimeType}</code>`;
    }

    _filterTextChanged(e) {
        this._searchTerm = (e.detail.value || '').trim();
        return this._filterGrid();
    }

    _filterGrid() {
        if (this._searchTerm === '') {
            this._filtered = this._resources;
            return;
        }

        this._filtered = this._resources.filter((resource) => {
           return this._match(resource.name, this._searchTerm) ||
                  this._match(resource.description, this._searchTerm) ||
                  this._match(resource.serverName, this._searchTerm) ||
                  this._match(resource.uri, this._searchTerm) ||
                  this._match(resource.mimeType, this._searchTerm);
        });
    }

    _match(value, term) {
        if (!value) {
            return false;
        }
        return value.toLowerCase().includes(term.toLowerCase());
    }

    _closeResultDialog() {
        this._resourceResult = null;
    }

    _closeInputDialog() {
        this._showInputDialog = false;
    }

    _readResource() {
        if (!this._selectedResource) return;

        const resource = this._selectedResource;

        this.jsonRpc.readResource({
            serverName: resource.serverName,
            uri: resource.uri,
            bearerToken: this._bearerToken,
            forceNewSession: this._forceNewSession
        }).then(jsonRpcResponse => {
            this._setResourceResult(jsonRpcResponse.result.response);
        });
    }

    _setResourceResult(result) {
        if (this._isJsonSerializable(result)) {
            this._resourceResult = JSON.stringify(result, null, 2);
        } else {
            this._resourceResult = result;
        }
    }

    _loadResources() {
        this.jsonRpc.getResourcesData()
            .then(jsonResponse => {
                this._resources = jsonResponse.result;
                this._filtered = this._resources;
            });
    }

    _isJsonSerializable(value) {
        return value !== null && (typeof value === 'object');
    }

}
customElements.define('qwc-mcp-resources', QwcMcpResources);
