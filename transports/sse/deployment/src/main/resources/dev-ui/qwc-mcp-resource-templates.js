
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { themeState } from 'theme-state';
import '@quarkus-webcomponents/codeblock';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/split-layout';
import { JsonRpc } from 'jsonrpc';

/**
 * This component shows the MCP resource templates.
 */
export class QwcMcpResourceTemplates extends LitElement {

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
        _resourceTemplates: { state: true },
        _selectedResourceTemplate: { state: true },
    };

    constructor() {
        super();
        // If not null then show the "call" form
        this._selectedResourceTemplate = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getResourceTemplatesData()
            .then(jsonResponse => {
                this._resourceTemplates = jsonResponse.result;
            });
    }

    render() {
        if (this._selectedResourceTemplate) {
            return this._renderResourceTemplateRead();
        } else {
            return this._renderResourceTemplates();
        }
    }

    _renderResourceTemplateRead() {
        return html`
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <p>Read resource template <strong>${this._selectedResourceTemplate.name}</strong> with URI:</p>
                <vaadin-text-field id="resource_template_uri" value="${this._selectedResourceTemplate.uriTemplate}">
                </vaadin-text-field>
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
        <vaadin-button @click="${this._showResourceTemplates}">
           Back to resource templates
        </vaadin-button>
        </div>
               `;
    }

    _renderResourceTemplates() {
        return html`
                <vaadin-grid .items="${this._resourceTemplates}" class="resources-table" theme="no-border" all-rows-visible>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="URI template" ${columnBodyRenderer(this._renderUriTemplate, [])}>
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
             <vaadin-button @click=${()=> this._selectResourceTemplate(resource)} theme="primary">
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

    _renderUriTemplate(resource) {
            return html`
                ${resource.uriTemplate}
                `;
        }

    _renderMimeType(resource) {
        return html`
            ${resource.mimeType}
        `;
    }

    _selectResourceTemplate(resource) {
        this._selectedResourceTemplate = resource;
    }

    _showResourceTemplates() {
        this._selectedResourceTemplate = null;
    }

    _readResource() {
        const uri = this.shadowRoot.getElementById('resource_template_uri');
        const responseTextArea = this.shadowRoot.getElementById('resource_response_text');
        this.jsonRpc.readResource({
            uri: uri.value,
        }).then(jsonRpcResponse => {
            responseTextArea.populatePrettyJson(this._prettyJson(jsonRpcResponse.result.response));
        });
    }

    _prettyJson(content) {
        return JSON.stringify(content, null, 2);
    }

}
customElements.define('qwc-mcp-resource-templates', QwcMcpResourceTemplates);