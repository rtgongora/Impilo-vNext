<#--
  Impilo recovery-code step — reached from "Try another way" when someone cannot use their
  authenticator. Same identity block and the same shape as the password and one-time-code
  steps, so falling back to a recovery code stays inside one sign-in rather than dropping
  into a differently-styled page at the moment someone is already having trouble.

  Keycloak asks for a SPECIFIC numbered code, not any code — recoveryAuthnCodesInputBean
  .codeNumber. The hint says so, because a person holding a printed list will otherwise
  type the first one and be told it is wrong.
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('recoveryCodeInput'); section>
<!-- template: login-recovery-authn-code-input.ftl (impilo) -->
    <#if section = "header">
        ${msg("impiloRecoveryTitle")}
    <#elseif section = "form">
        <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
            <div class="impilo-continue-as" id="impilo-continue-as">
                <span class="impilo-continue-as__label">${msg("impiloContinuingAs")}</span>
                <span class="impilo-continue-as__identity">${auth.attemptedUsername}</span>
                <a class="impilo-continue-as__switch" href="${url.loginRestartFlowUrl}">${msg("impiloNotYou")}</a>
            </div>
        </#if>
        <p class="impilo-step-hint">${msg("impiloRecoveryHint", recoveryAuthnCodesInputBean.codeNumber?c)}</p>
        <form id="kc-recovery-code-login-form" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <@field.input name="recoveryCodeInput" label=msg("auth-recovery-code-prompt", recoveryAuthnCodesInputBean.codeNumber?c) autofocus=true />

            <@buttons.loginButton />
        </form>
    </#if>
</@layout.registrationLayout>
