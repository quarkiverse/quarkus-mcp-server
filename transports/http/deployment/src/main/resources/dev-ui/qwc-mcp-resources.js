
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { themeState } from 'theme-state';
import '@quarkus-webcomponents/codeblock';
import 'qui/qui-alert.js';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/split-layout';
import { JsonRpc } from 'jsonrpc';

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
        .resources-table {
          padding-bottom: 10px;
          height: 100%;
        }
        .resource-read {
          padding-left: 0.5em;
        }
        code {
          font-size: 85%;
        }
        div.buttons {
          margin-top: 2em;
        }
        `;

    static properties = {
        _resources: { state: true },
        _selectedResource: { state: true },
    };

    constructor() {
        super();
        // If not null then show the "call" form
        this._selectedResource = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getResourcesData()
            .then(jsonResponse => {
                this._resources = jsonResponse.result;
            });
    }

    render() {
        if (this._selectedResource) {
            return this._renderResourceRead();
        } else {
            return this._renderResources();
        }
    }

    _renderResourceRead() {
        return html`
        <div class="resource-read">
        <h3>Read resource: ${this._selectedResource.name}</h3>
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <vaadin-checkbox 
                    id="resource_force_new_session" 
                    label="Force new session" 
                    helper-text="Initialize a new MCP session for the request">
                </vaadin-checkbox>
                <vaadin-text-field 
                    id="resource_bearer_token"
                    label="Bearer Token"
                    value="" 
                    clear-button-visible
                    style="width: 97%;">
                    <vaadin-tooltip 
                        slot="tooltip" 
                        text="The Authorization header with the bearer token is automatically added to the HTTP POST request">
                    </vaadin-tooltip>
                </vaadin-text-field>
            </master-content>
            <detail-content style="width: 50%;">
                <qui-code-block id="resource_response_text" mode='json' showLineNumbers content=''
                    theme='${themeState.theme.name}'>
                </qui-code-block>
            </detail-content>
        </vaadin-split-layout>
        <div class="buttons">
        <vaadin-button @click="${this._readResource}" theme="primary">
           Read
        </vaadin-button>
        <vaadin-button @click="${this._showResources}">
           Back to resources
        </vaadin-button>
        </div>
        </div>
               `;
    }

    _renderResources() {
        return html`
                <qui-alert level="success">This view contains all Resources for all MCP server configurations. Resource filters are not applied.</qui-alert>
                <vaadin-grid .items="${this._resources}" class="resources-table" theme="no-border wrap-cell-content" all-rows-visible>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="URI" ${columnBodyRenderer(this._renderUri, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="Name" ${columnBodyRenderer(this._renderName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="MCP Server" ${columnBodyRenderer(this._renderServerName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="25rem" flex-grow="0" header="Description" ${columnBodyRenderer(this._renderDescription,
                        [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="Mime type" ${columnBodyRenderer(this._renderMimeType, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="Actions" ${columnBodyRenderer(this._renderActions, [])}>
                    </vaadin-grid-column>
                </vaadin-grid>
                `;
    }

    _renderActions(resource) {
         return html`
             <vaadin-button @click=${()=> this._selectResource(resource)} theme="primary">
                 Read    
             </vaadin-button>
         `;
     }
    
    _renderName(resource) {
        return html`
            ${resource.name}
        `;
    }
    
    _renderServerName(resource) {
            return html`
                ${resource.serverName}
            `;
    }

    _renderDescription(resource) {
        return html`
                ${resource.description}
            `;
    }

    _renderUri(resource) {
            return html`
                ${resource.uri}
                `;
        }

    _renderMimeType(resource) {
        return html`
            ${resource.mimeType}
        `;
    }

    _selectResource(resource) {
        this._selectedResource = resource;
    }

    _showResources() {
        this._selectedResource = null;
    }

    _readResource() {
        const bearerToken = this.shadowRoot.getElementById('resource_bearer_token');
        const forceNewSession = this.shadowRoot.getElementById('resource_force_new_session');
        const responseTextArea = this.shadowRoot.getElementById('resource_response_text');
        this.jsonRpc.readResource({
            serverName: this._selectedResource.serverName,
            uri: this._selectedResource.uri,
            bearerToken: bearerToken.value,
            forceNewSession: forceNewSession.checked
        }).then(jsonRpcResponse => {
            responseTextArea.populatePrettyJson(this._prettyJson(jsonRpcResponse.result.response));
        });
    }

    _prettyJson(content) {
        return JSON.stringify(content, null, 2);
    }

}
customElements.define('qwc-mcp-resources', QwcMcpResources);