
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
        .prompt-completions-table {
          padding-bottom: 10px;
          height: 100%;
        }
        .prompt-complete {
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
        _completions: { state: true },
        _selectedCompletion: { state: true },
    };

    constructor() {
        super();
        // If not null then show the "complete" form
        this._selectedCompletion = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getPromptCompletionsData()
            .then(jsonResponse => {
                this._completions = jsonResponse.result;
            });
    }

    render() {
        if (this._selectedCompletion) {
            return this._renderPromptComplete();
        } else {
            return this._renderPromptCompletions();
        }
    }

    _renderPromptComplete() {
        return html`
        <div class="prompt-complete">
        <h3>Complete prompt: ${this._selectedCompletion.name}</h3>
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <vaadin-checkbox 
                    id="prompt_force_new_session" 
                    label="Force new session" 
                    helper-text="Initialize a new MCP session for the request">
                </vaadin-checkbox>    
                <vaadin-text-field 
                    id="prompt_bearer_token"
                    label="Bearer Token"
                    value="" 
                    clear-button-visible
                    style="width: 97%;">
                    <vaadin-tooltip 
                        slot="tooltip" 
                        text="The Authorization header with the bearer token is automatically added to the HTTP POST request">
                    </vaadin-tooltip>
                </vaadin-text-field>
                <vaadin-text-field
                    id="prompt_completion_text"
                    value=""
                    label="Argument: ${this._selectedCompletion.argumentName}"
                    style="font-family: monospace;width: 15em;">
                </vaadin-text-field>
            </master-content>
            <detail-content style="width: 50%;">
                <qui-code-block id="prompt_completion_response_text" mode='json' showLineNumbers content=''
                    theme='${themeState.theme.name}'>
                </qui-code-block>
            </detail-content>
        </vaadin-split-layout>
        <div class="buttons">
        <vaadin-button @click="${this._completePrompt}" theme="primary">
           Complete
        </vaadin-button>
        <vaadin-button @click="${this._showPromptCompletions}">
           Back to prompt completions
        </vaadin-button>
        </div>
        </div>
               `;
    }

    _renderPromptCompletions() {
        return html`
                <qui-alert level="success">This view contains all Prompt completions for all MCP server configurations.</qui-alert>
                <vaadin-grid .items="${this._completions}" class="prompt-completions-table" theme="no-border wrap-cell-content" all-rows-visible>
                    <vaadin-grid-column auto-width header="Prompt" ${columnBodyRenderer(this._renderName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width header="Completed argument" ${columnBodyRenderer(this._renderArgName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column width="10rem" flex-grow="0" header="MCP Server" ${columnBodyRenderer(this._renderServerName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width header="Actions" ${columnBodyRenderer(this._renderActions, [])}>
                    </vaadin-grid-column>
                </vaadin-grid>
                `;
    }

    _renderActions(completion) {
         return html`
             <vaadin-button @click=${()=> this._selectCompletion(completion)} theme="primary">
                 Complete    
             </vaadin-button>
         `;
     }
    
    _renderName(completion) {
        return html`
            ${completion.name}
        `;
    }
    
    _renderServerName(completion) {
            return html`
                ${completion.serverName}
            `;
    }
    
    _renderArgName(completion) {
        return html`
            <code>${completion.argumentName}</code>
        `;
    }

    _selectCompletion(completion) {
        this._selectedCompletion = completion;
    }

    _showPromptCompletions() {
        this._selectedCompletion = null;
    }

    _completePrompt() {
        const bearerToken = this.shadowRoot.getElementById('prompt_bearer_token');
        const forceNewSession = this.shadowRoot.getElementById('prompt_force_new_session');
        const requestInput = this.shadowRoot.getElementById('prompt_completion_text');
        const responseTextArea = this.shadowRoot.getElementById('prompt_completion_response_text');
        const content = requestInput.value;
        this.jsonRpc.completePrompt({
            name: this._selectedCompletion.name,
            argumentName: this._selectedCompletion.argumentName,
            argumentValue: content,
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
customElements.define('qwc-mcp-prompt-completions', QwcMcpPromptCompletions);