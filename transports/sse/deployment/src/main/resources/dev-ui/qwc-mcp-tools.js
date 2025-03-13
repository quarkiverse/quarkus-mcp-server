
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { themeState } from 'theme-state';
import '@quarkus-webcomponents/codeblock';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/split-layout';
import { JsonRpc } from 'jsonrpc';

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
        .tools-table {
          padding-bottom: 10px;
          height: 100%;
        }
        .tool-call {
          padding-left: 0.5em;
        }
        code {
          font-size: 85%;
        }
        `;

    static properties = {
        _tools: { state: true },
        _selectedTool: { state: true },
    };

    constructor() {
        super();
        // If not null then show the "call" form
        this._selectedTool = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getToolsData()
            .then(jsonResponse => {
                this._tools = jsonResponse.result;
            });
    }

    render() {
        if (this._selectedTool) {
            return this._renderToolCall();
        } else {
            return this._renderTools();
        }
    }

    _renderToolCall() {
        return html`
        <div class="tool-call">
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <p>Calling tool <strong>${this._selectedTool.name}</strong> with arguments:</p>
                <qui-code-block id="tool_request_text" mode='json' showLineNumbers
                    content='${this._prettyJson(this._selectedTool.inputPrototype)}'
                    value='${this._prettyJson(this._selectedTool.inputPrototype)}' 
                    theme='${themeState.theme.name}' editable>
                </qui-code-block>
            </master-content>
            <detail-content style="width: 50%;">
                <qui-code-block id="tool_response_text" mode='json' showLineNumbers content='\n\n\n\n\n'
                    theme='${themeState.theme.name}'>
                </qui-code-block>
            </detail-content>
        </vaadin-split-layout>
        <div>
        <vaadin-button @click="${this._callTool}" theme="primary">
           Call
        </vaadin-button>
        <vaadin-button @click="${this._showTools}">
           Back to tools
        </vaadin-button>
        </div>
        </div>
               `;
    }

    _renderTools() {
        return html`
                <vaadin-grid .items="${this._tools}" class="tools-table" theme="no-border" all-rows-visible>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="Name" ${columnBodyRenderer(this._renderName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="25rem" flex-grow="0" header="Description" ${columnBodyRenderer(this._renderDescription,
                        [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width header="Arguments" ${columnBodyRenderer(this._renderArgs, [])} resizable>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="Actions" ${columnBodyRenderer(this._renderActions, [])}>
                    </vaadin-grid-column>
                </vaadin-grid>
                `;
    }

    _renderActions(tool) {
         return html`
             <vaadin-button @click=${()=> this._selectTool(tool)} theme="primary">
                 Call    
             </vaadin-button>
         `;
     }
    
    _renderName(tool) {
        return html`
            ${tool.name}
        `;
    }

    _renderDescription(tool) {
        return html`
                ${tool.description}
            `;
    }

    _renderArgs(tool) {
        if (tool.args) {
            return html`
                                <vaadin-grid .items="${tool.args}" class="tool-args-table" theme="row-stripes" all-rows-visible>
                                    <vaadin-grid-column auto-width header="Name" ${columnBodyRenderer(this._renderArgName, [])} resizable>
                                    </vaadin-grid-column>
                                    <vaadin-grid-column auto-width header="Description" ${columnBodyRenderer(this._renderArgDescription, [])} resizable>
                                    </vaadin-grid-column>
                                    <vaadin-grid-column auto-width header="Required" ${columnBodyRenderer(this._renderArgRequired, [])} resizable>
                                    </vaadin-grid-column>
                                    <vaadin-grid-column auto-width header="Type" ${columnBodyRenderer(this._renderArgType, [])} resizable>
                                    </vaadin-grid-column>
                                </vaadin-grid>
                                `;
        } else {
            return html``;
        }
    }

    _renderArgName(arg) {
        return html`
            <code>${arg.name}</code>
        `;
    }

    _renderArgDescription(arg) {
        return html`
                ${arg.description}
            `;
    }

    _renderArgRequired(arg) {
        return html`
                    ${arg.required ? 'Yes' : 'No'}
                `;
    }

    _renderArgType(arg) {
        return html`
                        <code>${arg.type}</code>
                    `;
    }

    _selectTool(tool) {
        this._selectedTool = tool;
    }

    _showTools() {
        this._selectedTool = null;
    }

    _callTool() {
        const requestTextArea = this.shadowRoot.getElementById('tool_request_text');
        const responseTextArea = this.shadowRoot.getElementById('tool_response_text');
        const content = requestTextArea.getAttribute('value');
        this.jsonRpc.callTool({
            name: this._selectedTool.name,
            args: content
        }).then(jsonRpcResponse => {
            responseTextArea.populatePrettyJson(this._prettyJson(jsonRpcResponse.result.response));
        });
    }

    _prettyJson(content) {
        return JSON.stringify(content, null, 2);
    }

}
customElements.define('qwc-mcp-tools', QwcMcpTools);