
import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { themeState } from 'theme-state';
import '@quarkus-webcomponents/codeblock';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/split-layout';
import { JsonRpc } from 'jsonrpc';

/**
 * This component shows the MCP resource template completions.
 */
export class QwcMcpResourceTemplateCompletions extends LitElement {

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
        this.jsonRpc.getResourceTemplateCompletionsData()
            .then(jsonResponse => {
                this._completions = jsonResponse.result;
            });
    }

    render() {
        if (this._selectedCompletion) {
            return this._renderResourceTemplateComplete();
        } else {
            return this._renderResourceTemplateCompletions();
        }
    }

    _renderResourceTemplateComplete() {
        return html`
        <div class="prompt-get">
        <vaadin-split-layout>
            <master-content style="width: 50%;">
                <p>Complete the argument <strong>${this._selectedCompletion.argumentName}</strong> for resource template <strong>${this._selectedCompletion.name}</strong>:
                <br>
                            <vaadin-text-field
                                id="resource_template_completion_text"
                                value=""
                                placeholder="Value to autocomplete"
                                style="font-family: monospace;width: 15em;"
                             >
                </p>
            </master-content>
            <detail-content style="width: 50%;">
                <qui-code-block id="resource_template_completion_response_text" mode='json' showLineNumbers content='\n\n\n\n\n'
                    theme='${themeState.theme.name}'>
                </qui-code-block>
            </detail-content>
        </vaadin-split-layout>
        <div>
        <vaadin-button @click="${this._completeResourceTemplate}" theme="primary">
           Complete
        </vaadin-button>
        <vaadin-button @click="${this._showResourceTemplateCompletions}">
           Back to resource template completions
        </vaadin-button>
        </div>
        </div>
               `;
    }

    _renderResourceTemplateCompletions() {
        return html`
                <vaadin-grid .items="${this._completions}" class="prompts-table" theme="no-border" all-rows-visible>
                    <vaadin-grid-column auto-width header="Resource template" ${columnBodyRenderer(this._renderName, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width header="Completed argument" ${columnBodyRenderer(this._renderArgName, [])}>
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
    
    _renderArgName(completion) {
        return html`
            <code>${completion.argumentName}</code>
        `;
    }

    _selectCompletion(completion) {
        this._selectedCompletion = completion;
    }

    _showResourceTemplateCompletions() {
        this._selectedCompletion = null;
    }

    _completeResourceTemplate() {
        const requestInput = this.shadowRoot.getElementById('resource_template_completion_text');
        const responseTextArea = this.shadowRoot.getElementById('resource_template_completion_response_text');
        const content = requestInput.value;
        this.jsonRpc.completeResourceTemplate({
            name: this._selectedCompletion.name,
            argumentName: this._selectedCompletion.argumentName,
            argumentValue: content
        }).then(jsonRpcResponse => {
            responseTextArea.populatePrettyJson(this._prettyJson(jsonRpcResponse.result.response));
        });
    }

    _prettyJson(content) {
        return JSON.stringify(content, null, 2);
    }

}
customElements.define('qwc-mcp-resource-template-completions', QwcMcpResourceTemplateCompletions);