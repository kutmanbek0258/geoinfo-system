const {defineConfig} = require('@vue/cli-service');

module.exports = defineConfig({
    transpileDependencies: true,
    publicPath: "/",

    devServer: {
        port: 8181,
        client: {
            overlay: false
        }
    },

    css: {
        extract: process.env.VUE_APP_NODE_ENV !== "development"
    },

    pluginOptions: {
        vuetify: {
            styles: {
                configFile: "src/assets/scss/settings.scss"
            }
        }
    }
});
