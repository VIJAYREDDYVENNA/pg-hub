/* Webpack 5 build for the React frontend (no Vite, no CRA). */
const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');

module.exports = (env, argv) => {
  const prod = argv.mode === 'production';
  const backend = process.env.BACKEND_URL || 'http://localhost:8080';
  return {
    entry: './src/index.js',
    output: {
      path: path.resolve(__dirname, 'build'),
      filename: prod ? 'static/js/[name].[contenthash:8].js' : 'static/js/[name].js',
      chunkFilename: prod ? 'static/js/[name].[contenthash:8].chunk.js' : 'static/js/[name].chunk.js',
      assetModuleFilename: 'static/media/[name].[hash:8][ext]',
      publicPath: '/',
      clean: true,
    },
    devtool: prod ? 'source-map' : 'eval-cheap-module-source-map',
    resolve: { extensions: ['.js', '.jsx'] },
    module: {
      rules: [
        { test: /\.jsx?$/, exclude: /node_modules/, use: 'babel-loader' },
        { test: /\.css$/, use: [prod ? MiniCssExtractPlugin.loader : 'style-loader', 'css-loader'] },
        { test: /\.(png|jpe?g|gif|svg|webp)$/i, type: 'asset' },
        { test: /\.(woff2?|eot|ttf|otf)$/i, type: 'asset/resource' },
      ],
    },
    plugins: [
      new HtmlWebpackPlugin({ template: './public/index.html', favicon: './public/favicon.svg' }),
      new CopyWebpackPlugin({
        patterns: [{ from: 'public', globOptions: { ignore: ['**/index.html', '**/favicon.svg'] } }],
      }),
      prod && new MiniCssExtractPlugin({ filename: 'static/css/[name].[contenthash:8].css' }),
    ].filter(Boolean),
    optimization: {
      minimizer: ['...', new CssMinimizerPlugin()],
      splitChunks: { chunks: 'all' },
    },
    performance: { hints: false },
    devServer: {
      port: 3000,
      host: 'localhost',
      historyApiFallback: true,
      hot: true,
      open: true,
      client: { overlay: { errors: true, warnings: false } },
      // Same-origin API calls in development: /api is forwarded to the Spring Boot backend.
      proxy: [{ context: ['/api'], target: backend, changeOrigin: false }],
    },
  };
};
