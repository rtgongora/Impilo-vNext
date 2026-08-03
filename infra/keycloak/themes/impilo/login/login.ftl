<#--
  Impilo login page — one seamless journey, not a second door.

  The shell's ProgressiveAuthForm collects the identifier and hands off to Keycloak with
  `login_hint`. The stock keycloak.v2 template then renders an EDITABLE username box next
  to the password box, so a person who has just typed their identifier is shown what looks
  like the same question again. That reads as a separate credential prompt and breaks the
  "one front door" doctrine.

  This override changes presentation only. When an identifier arrived (login_hint), it is
  shown as CONFIRMED CONTEXT with a way to change it, and only the secret is asked for.
  Keycloak still hosts and verifies every credential; the shell never sees a password.

  Deliberately shows the IDENTIFIER THE PERSON TYPED, never a resolved display name: at
  this point Keycloak has not authenticated anyone, so rendering a real name next to a
  guessed username would leak name-from-username to anyone probing accounts.
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "social-providers.ftl" as identityProviders>
<#import "passkeys.ftl" as passkeys>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
<!-- template: login.ftl (impilo) -->

    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <#if realm.password>
                <#-- An identifier arrived from the shell and there is no field error to correct. -->
                <#assign continueAs = !usernameHidden?? && (login.username!'')?has_content
                                      && !messagesPerField.existsError('username')>
                <form id="kc-form-login" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate="novalidate">
                    <#if continueAs>
                        <div class="impilo-continue-as" id="impilo-continue-as">
                            <span class="impilo-continue-as__label">${msg("impiloContinuingAs")}</span>
                            <span class="impilo-continue-as__identity" id="impilo-continue-identity">${login.username}</span>
                            <a class="impilo-continue-as__switch" href="${url.loginRestartFlowUrl}">${msg("impiloNotYou")}</a>
                        </div>
                        <#-- Keycloak still needs the username submitted; it is simply not re-asked. -->
                        <input type="hidden" id="username" name="username" value="${login.username}" />
                        <@field.password name="password" label=msg("password") error=messagesPerField.getFirstError('username','password') forgotPassword=realm.resetPasswordAllowed autofocus=true autocomplete="current-password">
                            <#if realm.rememberMe>
                                <@field.checkbox name="rememberMe" label=msg("rememberMe") value=login.rememberMe?? />
                            </#if>
                        </@field.password>
                    <#elseif !usernameHidden??>
                        <#assign label>
                            <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                        </#assign>
                        <@field.input name="username" label=label error=messagesPerField.getFirstError('username','password')
                            autofocus=true autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'username')}" value=login.username!'' />
                        <@field.password name="password" label=msg("password") error="" forgotPassword=realm.resetPasswordAllowed autofocus=usernameHidden?? autocomplete="current-password">
                            <#if realm.rememberMe && !usernameHidden??>
                                <@field.checkbox name="rememberMe" label=msg("rememberMe") value=login.rememberMe?? />
                            </#if>
                        </@field.password>
                    <#else>
                        <@field.password name="password" label=msg("password") forgotPassword=realm.resetPasswordAllowed autofocus=usernameHidden?? autocomplete="current-password">
                            <#if realm.rememberMe && !usernameHidden??>
                                <@field.checkbox name="rememberMe" label=msg("rememberMe") value=login.rememberMe?? />
                            </#if>
                        </@field.password>
                    </#if>

                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                    <@buttons.loginButton />
                </form>
            </#if>
            </div>
        </div>
        <@passkeys.conditionalUIData />
    <#elseif section = "socialProviders" >
        <#if realm.password && social.providers?? && social.providers?has_content>
            <@identityProviders.show social=social/>
        </#if>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
