
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { themeState } from 'theme-state';
import '@quarkus-webcomponents/codeblock';
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
        code {
          font-size: 85%;
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
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <p>Read resource <strong>${this._selectedResource.name}</strong> with URI <code>${this._selectedResource.uri}</code></p>
            </master-content>
            <detail-content style="width: 50%;">
                <qui-code-block id="resource_response_text" mode='json' showLineNumbers content='\n\n\n\n\n'
                    theme='${themeState.theme.name}'>
                </qui-code-block>
            </detail-content>
        </vaadin-split-layout>
        <div>
        <vaadin-button @click="${this._readResource}" theme="primary">
           Read
        </vaadin-button>
        <vaadin-button @click="${this._showResources}">
           Back to resources
        </vaadin-button>
        </div>
               `;
    }

    _renderResources() {
        return html`
                <vaadin-grid .items="${this._resources}" class="resources-table" theme="no-border" all-rows-visible>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="URI" ${columnBodyRenderer(this._renderUri, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="Name" ${columnBodyRenderer(this._renderName, [])}>
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
        const responseTextArea = this.shadowRoot.getElementById('resource_response_text');
        this.jsonRpc.readResource({
            uri: this._selectedResource.uri,
        }).then(jsonRpcResponse => {
            responseTextArea.populatePrettyJson(this._prettyJson(jsonRpcResponse.result.response));
        });
    }

    _prettyJson(content) {
        return JSON.stringify(content, null, 2);
    }

}
customElements.define('qwc-mcp-resources', QwcMcpResources);