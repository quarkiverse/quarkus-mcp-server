
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { themeState } from 'theme-state';
import '@quarkus-webcomponents/codeblock';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/split-layout';
import { JsonRpc } from 'jsonrpc';

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
        .prompts-table {
          padding-bottom: 10px;
          height: 100%;
        }
        .prompt-get {
          padding-left: 0.5em;
        }
        code {
          font-size: 85%;
        }
        `;

    static properties = {
        _prompts: { state: true },
        _selectedPrompt: { state: true },
    };

    constructor() {
        super();
        // If not null then show the "get" form
        this._selectedPrompt = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getPromptsData()
            .then(jsonResponse => {
                this._prompts = jsonResponse.result;
            });
    }

    render() {
        if (this._selectedPrompt) {
            return this._renderPromptGet();
        } else {
            return this._renderPrompts();
        }
    }

    _renderPromptGet() {
        return html`
        <div class="prompt-get">
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <p>Get prompt <strong>${this._selectedPrompt.name}</strong> with arguments:</p>
                <qui-code-block id="prompt_request_text" mode='json' showLineNumbers
                    content='${this._prettyJson(this._selectedPrompt.inputPrototype)}'
                    value='${this._prettyJson(this._selectedPrompt.inputPrototype)}' 
                    theme='${themeState.theme.name}' editable>
                </qui-code-block>
            </master-content>
            <detail-content style="width: 50%;">
                <qui-code-block id="prompt_response_text" mode='json' showLineNumbers content='\n\n\n\n\n'
                    theme='${themeState.theme.name}'>
                </qui-code-block>
            </detail-content>
        </vaadin-split-layout>
        <div>
        <vaadin-button @click="${this._getPrompt}" theme="primary">
           Get
        </vaadin-button>
        <vaadin-button @click="${this._showPrompts}">
           Back to prompts
        </vaadin-button>
        </div>
        </div>
               `;
    }

    _renderPrompts() {
        return html`
                <vaadin-grid .items="${this._prompts}" class="prompts-table" theme="no-border" all-rows-visible>
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

    _renderActions(prompt) {
         return html`
             <vaadin-button @click=${()=> this._selectPrompt(prompt)} theme="primary">
                 Get    
             </vaadin-button>
         `;
     }
    
    _renderName(prompt) {
        return html`
            ${prompt.name}
        `;
    }

    _renderDescription(prompt) {
        return html`
                ${prompt.description}
            `;
    }

    _renderArgs(prompt) {
        if (prompt.arguments) {
            return html`
                                <vaadin-grid .items="${prompt.arguments}" class="prompt-args-table" theme="row-stripes" all-rows-visible>
                                    <vaadin-grid-column auto-width header="Name" ${columnBodyRenderer(this._renderArgName, [])} resizable>
                                    </vaadin-grid-column>
                                    <vaadin-grid-column auto-width header="Description" ${columnBodyRenderer(this._renderArgDescription, [])} resizable>
                                    </vaadin-grid-column>
                                    <vaadin-grid-column auto-width header="Required" ${columnBodyRenderer(this._renderArgRequired, [])} resizable>
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

    _selectPrompt(prompt) {
        this._selectedPrompt = prompt;
    }

    _showPrompts() {
        this._selectedPrompt = null;
    }

    _getPrompt() {
        const requestTextArea = this.shadowRoot.getElementById('prompt_request_text');
        const responseTextArea = this.shadowRoot.getElementById('prompt_response_text');
        const content = requestTextArea.getAttribute('value');
        this.jsonRpc.getPrompt({
            name: this._selectedPrompt.name,
            args: content
        }).then(jsonRpcResponse => {
            responseTextArea.populatePrettyJson(this._prettyJson(jsonRpcResponse.result.response));
        });
    }

    _prettyJson(content) {
        return JSON.stringify(content, null, 2);
    }

}
customElements.define('qwc-mcp-prompts', QwcMcpPrompts);